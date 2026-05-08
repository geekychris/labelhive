package com.datalabel.sdk;

/**
 * Identifiers for labeling task templates. Built-ins are listed below as constants;
 * plugin authors can pick any non-empty {@code [A-Z0-9_]} string for their own type.
 *
 * <p><strong>Why constants instead of an enum?</strong> An enum would force every
 * plugin to modify the core to register a new id. By using {@code String} ids we
 * let third-party JARs add a {@link LabelingTemplate} bean to the Spring context
 * with any id they want — the {@link TemplateRegistry} picks it up at startup.</p>
 *
 * <p>Built-in ids:</p>
 * <ul>
 *   <li>{@link #MULTIPLE_CHOICE}      — single- or multi-select from a fixed list.</li>
 *   <li>{@link #FREE_TEXT}            — natural language answer.</li>
 *   <li>{@link #IMAGE_CLASSIFICATION} — like multi-choice but the unit payload carries an image URL.</li>
 *   <li>{@link #SEARCH_RELEVANCE}     — graded query/result relevance (per-query NDCG).</li>
 *   <li>{@link #SPAN_ANNOTATION}      — pick spans inside a text (NER, keyword tagging).</li>
 *   <li>{@link #BOUNDING_BOX}         — draw boxes on an image.</li>
 * </ul>
 */
public final class TemplateType {

    public static final String MULTIPLE_CHOICE      = "MULTIPLE_CHOICE";
    public static final String FREE_TEXT            = "FREE_TEXT";
    public static final String IMAGE_CLASSIFICATION = "IMAGE_CLASSIFICATION";
    public static final String SEARCH_RELEVANCE     = "SEARCH_RELEVANCE";
    public static final String SPAN_ANNOTATION      = "SPAN_ANNOTATION";
    public static final String BOUNDING_BOX         = "BOUNDING_BOX";

    private TemplateType() {}
}
