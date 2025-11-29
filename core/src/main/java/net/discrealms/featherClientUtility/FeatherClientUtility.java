package net.discrealms.featherClientUtility;

public final class FeatherClientUtility {

    private FeatherClientUtility() {
    }

    public static String getVersion() {
        return FeatherClientUtility.class.getPackage().getImplementationVersion();
    }
}
