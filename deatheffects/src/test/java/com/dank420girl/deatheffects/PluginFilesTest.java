package com.dank420girl.deatheffects;

import com.dank420girl.core.Variant;
import com.dank420girl.core.testing.PluginChecks;
import org.junit.Test;

/** The plugin.yml Gradle filled in for this plugin. Everything else is tested in the core. */
public class PluginFilesTest {
    @Test
    public void pluginYmlIsComplete() throws Exception {
        PluginChecks.checkPluginYml(Variant.DEATH_EFFECTS, "com.dank420girl.deatheffects.DeathEffectsPlugin");
    }
}
