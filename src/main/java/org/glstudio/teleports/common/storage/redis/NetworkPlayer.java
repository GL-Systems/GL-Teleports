package org.glstudio.teleports.common.storage.redis;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class NetworkPlayer {

    private UUID uuid;
    private String name;
    private String server;
}
