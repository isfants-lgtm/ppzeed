package com.zeed.ppzeed;

public enum ItemType {
    ANTI_ELYTRA("anti_elytra", "anti-elytra", "FEATHER"),
    TRAP_TEIA("trap_teia", "trap-teia", "BONE"),
    CONFUSAO("confusao", "confusao", "ENDER_EYE");

    /** Identificador gravado dentro do item. */
    public final String id;
    /** Nome da secao no config.yml (itens.<cfg>). */
    public final String cfg;
    public final String defMaterial;

    ItemType(String id, String cfg, String defMaterial) {
        this.id = id;
        this.cfg = cfg;
        this.defMaterial = defMaterial;
    }

    public static ItemType fromId(String id) {
        for (ItemType t : values()) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }
}
