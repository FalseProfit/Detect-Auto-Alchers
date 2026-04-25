package com.detectautoalchers;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class DetectAutoAlchersPluginLauncher
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(DetectAutoAlchersPlugin.class);
        RuneLite.main(args);
    }
}
