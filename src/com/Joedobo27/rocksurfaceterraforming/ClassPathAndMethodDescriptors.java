package com.joedobo27.RockSurfaceTerraformingMod;

public enum ClassPathAndMethodDescriptors {
    CREATION_ENTRY_CLASS("com.wurmonline.server.items.CreationEntry", "CreationEntry", ""),
    TERRAFORMING_CLASS("com.wurmonline.server.behaviours.Terraforming", "Terraforming", ""),
    CAVE_TILE_BEHAVIOUR_CLASS("com.wurmonline.server.behaviours.CaveTileBehaviour", "CaveTileBehaviour", ""),
    CHECK_SANE_AMOUNTS_METHOD("","checkSaneAmounts",
            "(Lcom/wurmonline/server/items/Item;ILcom/wurmonline/server/items/Item;ILcom/wurmonline/server/items/ItemTemplate;Lcom/wurmonline/server/creatures/Creature;Z)V"),
    DIG_METHOD("", "dig",
            "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IIIFLcom/wurmonline/mesh/MeshIO;)Z"),
    RAISE_ROCK_LEVEL_METHOD("", "raiseRockLevel", "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IIFLcom/wurmonline/server/behaviours/Action;)Z"),
    TILE_ROCK_ACTION_METHOD("", "action", "(Lcom/wurmonline/server/behaviours/Action;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IIZIISF)Z"),
    TILE_ROCK_BEHAVIOUR_CLASS("com.wurmonline.server.behaviours.TileRockBehaviour", "TileRockBehaviour", "");

    private String path;
    private String name;
    private String descriptor;

    ClassPathAndMethodDescriptors(String path, String name, String descriptor){
        this.path = path;
        this.name = name;
        this.descriptor = descriptor;
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public String getDescriptor() {
        return descriptor;
    }
}
