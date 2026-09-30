package edu.vit.results.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "app_setting")
public class Setting {

    public static final String RESULTS_PUBLISHED = "results_published";

    @Id
    @Column(name = "setting_key", length = 60)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 200)
    private String value;

    protected Setting() {}

    public Setting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
