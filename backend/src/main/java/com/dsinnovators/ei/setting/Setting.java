package com.dsinnovators.ei.setting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One value of one setting, in force from a quarter until a row with a later quarter replaces it.
 * A value is changed by SQL or by a migration, never from code.
 */
@Entity
@Table(name = "setting")
class Setting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // "key" and "value" are keywords in JPQL, so the attributes have other names than the columns.
    @Column(name = "key")
    private String settingKey;

    private String validFromQuarter;

    @Column(name = "value")
    @JdbcTypeCode(SqlTypes.JSON)
    private String settingValue;

    private String description;

    protected Setting() {}

    String getSettingKey() {
        return settingKey;
    }

    String getValidFromQuarter() {
        return validFromQuarter;
    }

    /** The value as JSON text. */
    String getSettingValue() {
        return settingValue;
    }

    String getDescription() {
        return description;
    }
}
