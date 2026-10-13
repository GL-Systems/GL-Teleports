package org.glstudio.teleports.modules.migration.playerwarps;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Reads the material out of a PlayerWarps icon without the server.
 *
 * PlayerWarps stores an icon as Base64 of a Java-serialized {@code Map[]} with one entry per item. On Paper the
 * map holds the item under "paper" as {@code ItemStack#serializeAsBytes()} - gzip-compressed NBT whose root
 * compound has the item id; older servers stored {@code ItemStack#serialize()} with the material under "type",
 * and with ProtocolLib's modded serialization it is under "moddedItem", which only ProtocolLib can read.
 *
 * Only the material survives: this plugin keeps an icon as a material name, so a custom name, lore,
 * enchantments or a player head's skin are reported as not carried over.
 *
 * The serialized stream is read through an allow-list of plain JDK value classes, so no other class from the
 * source database can be instantiated.
 */
final class PlayerWarpsIcon {
    private static final int MAX_STREAM_BYTES = 1 << 20;
    private static final int MAX_NBT_BYTES = 4 << 20;
    private static final int MAX_NBT_DEPTH = 64;

    private static final Set<String> ALLOWED_CLASSES = Set.of(
            "[Ljava.util.Map;", "java.util.Map", "java.util.HashMap", "java.util.LinkedHashMap",
            // The tables HashMap and ArrayList allocate while reading themselves back.
            "[Ljava.util.Map$Entry;", "[Ljava.lang.Object;",
            "java.util.ArrayList", "java.util.LinkedList", "java.lang.String", "[B", "java.lang.Number",
            "java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.Byte", "java.lang.Double",
            "java.lang.Float", "java.lang.Boolean",
            // BukkitObjectOutputStream wraps nested item meta in this plain map holder.
            "org.bukkit.util.io.Wrapper");

    private PlayerWarpsIcon() {
    }

    /**
     * @param material   the material name (e.g. GOLD_BLOCK), or null when there is no icon or it could not be read
     * @param customised the item carried more than its material - a name, lore, enchantments, a head skin...
     * @param problem    why the icon could not be read, or null
     */
    record Result(String material, boolean customised, String problem) {
        static final Result NONE = new Result(null, false, null);

        static Result failed(String problem) {
            return new Result(null, false, problem);
        }
    }

    static Result decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Result.NONE;
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException e) {
            return Result.failed("the icon is not valid Base64");
        }

        Object root;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            input.setObjectInputFilter(PlayerWarpsIcon::filter);
            root = input.readObject();
        } catch (IOException | ClassNotFoundException | RuntimeException e) {
            return Result.failed("the icon uses a serialized format that can only be read by the server that "
                    + "wrote it (" + e.getClass().getSimpleName() + ")");
        }

        if (!(root instanceof Object[] items) || items.length == 0 || !(items[0] instanceof Map<?, ?> item)) {
            return Result.failed("the icon is not a serialized item");
        }

        if (item.isEmpty()) {
            return Result.NONE;
        }

        if (item.get("paper") instanceof byte[] paper) {
            return fromPaperBytes(paper);
        }

        if (item.containsKey("moddedItem")) {
            return Result.failed("the icon was serialized by ProtocolLib, which only ProtocolLib can read");
        }

        if (item.get("type") instanceof String type && !type.isBlank()) {
            boolean customised = item.containsKey("meta") || item.containsKey("damage");
            return new Result(materialName(type), customised, null);
        }

        return Result.failed("the icon has no item type");
    }

    private static ObjectInputFilter.Status filter(ObjectInputFilter.FilterInfo info) {
        if (info.depth() > 32 || info.references() > 10_000 || info.streamBytes() > MAX_STREAM_BYTES) {
            return ObjectInputFilter.Status.REJECTED;
        }

        Class<?> type = info.serialClass();
        if (type == null) {
            return ObjectInputFilter.Status.UNDECIDED;
        }

        return ALLOWED_CLASSES.contains(type.getName())
                ? ObjectInputFilter.Status.ALLOWED
                : ObjectInputFilter.Status.REJECTED;
    }

    private static Result fromPaperBytes(byte[] bytes) {
        try (DataInputStream nbt = new DataInputStream(new LimitedInputStream(
                new GZIPInputStream(new ByteArrayInputStream(bytes)), MAX_NBT_BYTES))) {
            if (nbt.readByte() != TAG_COMPOUND) {
                return Result.failed("the icon's item data is not an NBT compound");
            }
            nbt.readUTF();

            String id = null;
            boolean customised = false;

            for (byte type = nbt.readByte(); type != TAG_END; type = nbt.readByte()) {
                String name = nbt.readUTF();

                if (type == TAG_STRING && name.equals("id")) {
                    id = nbt.readUTF();
                } else {
                    // Anything besides the id, the stack size and the data version is item customisation:
                    // "components" on 1.20.5+, "tag" before that.
                    if (!name.equals("count") && !name.equals("Count") && !name.equals("DataVersion")) {
                        customised = true;
                    }
                    skip(nbt, type, 0);
                }
            }

            if (id == null || id.isBlank()) {
                return Result.failed("the icon's item data has no item id");
            }

            int colon = id.indexOf(':');
            if (colon >= 0 && !id.substring(0, colon).equals("minecraft")) {
                return Result.failed("the icon is the modded item '" + id + "'");
            }

            return new Result(materialName(colon >= 0 ? id.substring(colon + 1) : id), customised, null);
        } catch (IOException | RuntimeException e) {
            return Result.failed("the icon's item data could not be read (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static String materialName(String id) {
        return id.trim().toUpperCase(Locale.ROOT);
    }

    private static final byte TAG_END = 0;
    private static final byte TAG_STRING = 8;
    private static final byte TAG_COMPOUND = 10;

    private static void skip(DataInputStream nbt, byte type, int depth) throws IOException {
        if (depth > MAX_NBT_DEPTH) {
            throw new IOException("NBT nested too deeply");
        }

        switch (type) {
            case 1 -> nbt.skipNBytes(1);
            case 2 -> nbt.skipNBytes(2);
            case 3, 5 -> nbt.skipNBytes(4);
            case 4, 6 -> nbt.skipNBytes(8);
            case 7 -> nbt.skipNBytes(length(nbt));
            case TAG_STRING -> nbt.skipNBytes(nbt.readUnsignedShort());
            case 9 -> {
                byte elementType = nbt.readByte();
                int size = length(nbt);
                for (int index = 0; index < size; index++) {
                    skip(nbt, elementType, depth + 1);
                }
            }
            case TAG_COMPOUND -> {
                for (byte inner = nbt.readByte(); inner != TAG_END; inner = nbt.readByte()) {
                    nbt.skipNBytes(nbt.readUnsignedShort());
                    skip(nbt, inner, depth + 1);
                }
            }
            case 11 -> nbt.skipNBytes(4L * length(nbt));
            case 12 -> nbt.skipNBytes(8L * length(nbt));
            case TAG_END -> {
            }
            default -> throw new IOException("unknown NBT tag " + type);
        }
    }

    private static int length(DataInputStream nbt) throws IOException {
        int length = nbt.readInt();
        if (length < 0 || length > MAX_NBT_BYTES) {
            throw new IOException("NBT length out of range");
        }
        return length;
    }

    /** Guards against a compressed payload that inflates to something enormous. */
    private static final class LimitedInputStream extends InputStream {
        private final InputStream delegate;
        private long remaining;

        private LimitedInputStream(InputStream delegate, long limit) {
            this.delegate = delegate;
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                throw new EOFException("item data larger than " + MAX_NBT_BYTES + " bytes");
            }
            int value = delegate.read();
            if (value >= 0) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0) {
                throw new EOFException("item data larger than " + MAX_NBT_BYTES + " bytes");
            }
            int read = delegate.read(buffer, offset, (int) Math.min(length, remaining));
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
