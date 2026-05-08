package com.datalabel.sdk;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * String-keyed lookup of {@link LabelingTemplate} beans. Spring's component scan
 * collects every {@code @Component LabelingTemplate} (built-in or third-party)
 * and the registry indexes them by {@link LabelingTemplate#type()}.
 *
 * <p>Adding a new id at runtime is impossible by design — types are validated at
 * job-creation time so the system can fail fast on a typo. To register a new id,
 * deploy a bean implementing {@link LabelingTemplate}.</p>
 */
@Component
public class TemplateRegistry {

    private final Map<String, LabelingTemplate> byType = new HashMap<>();

    public TemplateRegistry(List<LabelingTemplate> templates) {
        for (LabelingTemplate t : templates) {
            if (byType.put(t.type(), t) != null) {
                throw new IllegalStateException("Duplicate LabelingTemplate registered for type=" + t.type());
            }
        }
    }

    public LabelingTemplate get(String type) {
        LabelingTemplate t = byType.get(type);
        if (t == null) throw new IllegalArgumentException("No template registered for type=" + type
                + ". Registered: " + byType.keySet());
        return t;
    }

    /** All known template ids — useful for admin UIs that show available types. */
    public Set<String> registeredTypes() {
        return Set.copyOf(byType.keySet());
    }
}
