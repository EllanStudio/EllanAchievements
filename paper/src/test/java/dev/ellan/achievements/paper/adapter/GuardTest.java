package dev.ellan.achievements.paper.adapter;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class GuardTest {
    @Test
    void failureIsSwallowedAndLoggedOncePerWindow() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        List<LogRecord> records = new ArrayList<>();
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
            (proxy, method, args) -> method.getName().equals("getLogger") ? logger : null);

        String key = "GuardTest-" + System.nanoTime();
        assertThatCode(() -> Guard.run(plugin, key, () -> { throw new IllegalStateException("boom"); }))
            .doesNotThrowAnyException();
        Guard.run(plugin, key, () -> { throw new IllegalStateException("boom again"); });

        assertThat(records).hasSize(1);
        assertThat(records.getFirst().getThrown()).hasMessage("boom");
    }

    @Test
    void successfulBodyRuns() {
        List<String> ran = new ArrayList<>();
        Guard.run(null, "ok", () -> ran.add("yes"));
        assertThat(ran).containsExactly("yes");
    }
}
