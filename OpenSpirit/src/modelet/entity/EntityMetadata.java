package modelet.entity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import modelet.util.ReflactionUtil;

/**
 * Reads Jakarta Persistence annotations from an entity class and exposes them to the
 * SQL-generating side of Modelet. Only the annotation VOCABULARY is borrowed —
 * {@code @Table}, {@code @Id}, {@code @Column}, {@code @Transient}, {@code @Enumerated},
 * {@code @GeneratedValue} — the engine stays SQL-first; there is no JPQL, no EntityManager,
 * no proxy, no session cache.
 *
 * Every lookup falls back to the original convention methods, so entities written before
 * Modelet 3.0 behave exactly as they always did:
 *
 * <ul>
 * <li>{@code @Table(name)} falls back to {@link Entity#getTableName()}</li>
 * <li>{@code @Id} fields fall back to {@link Entity#getKeyNames()}</li>
 * <li>{@code @Transient} adds to (not replaces) {@link Entity#getExclusiveFields()}</li>
 * <li>{@code @GeneratedValue} falls back to "generated unless the entity implements
 * {@link SystemIncrementEntity}"; an {@code @Id} without {@code @GeneratedValue} means the
 * application supplies the key itself</li>
 * <li>{@code @Enumerated(STRING)} is the default even without the annotation (Modelet has
 * always stored enum names); annotate {@code @Enumerated(ORDINAL)} to store the ordinal</li>
 * </ul>
 *
 * Annotations are read from fields (field access), matching how Modelet has always
 * inspected entities.
 */
public final class EntityMetadata {

  private static final Map<Class<?>, EntityMetadata> CACHE = new ConcurrentHashMap<Class<?>, EntityMetadata>();

  private final String tableName;
  private final List<String> idFieldNames = new ArrayList<String>();
  private final boolean idGenerated;
  private final Map<String, String> columnByField = new HashMap<String, String>();
  private final Map<String, String> fieldByColumn = new HashMap<String, String>();
  private final Set<String> transientFields = new HashSet<String>();
  private final Map<String, EnumType> enumTypeByField = new HashMap<String, EnumType>();

  public static EntityMetadata of(Class<?> clazz) {

    EntityMetadata metadata = CACHE.get(clazz);
    if (metadata == null) {
      metadata = new EntityMetadata(clazz);
      CACHE.put(clazz, metadata);
    }
    return metadata;
  }

  private EntityMetadata(Class<?> clazz) {

    Table table = clazz.getAnnotation(Table.class);
    this.tableName = (table != null && table.name().length() > 0) ? table.name() : null;

    boolean generated = false;
    List fields = new ArrayList();
    ReflactionUtil.retrieveFields(fields, clazz);
    for (int i = 0; i < fields.size(); i++) {
      Field field = (Field) fields.get(i);
      String fieldName = field.getName();

      if (field.isAnnotationPresent(Transient.class))
        transientFields.add(fieldName);

      if (field.isAnnotationPresent(Id.class)) {
        idFieldNames.add(fieldName);
        if (field.isAnnotationPresent(GeneratedValue.class))
          generated = true;
      }

      Column column = field.getAnnotation(Column.class);
      if (column != null && column.name().length() > 0) {
        columnByField.put(fieldName, column.name());
        fieldByColumn.put(column.name().toLowerCase(), fieldName);
      }

      Enumerated enumerated = field.getAnnotation(Enumerated.class);
      if (enumerated != null)
        enumTypeByField.put(fieldName.toLowerCase(), enumerated.value());
    }
    this.idGenerated = generated;
  }

  /** Table name from {@code @Table}, or the entity's own getTableName(). */
  public String resolveTableName(Entity entity) {
    return tableName != null ? tableName : entity.getTableName();
  }

  /** Key field names from {@code @Id} markers, or the entity's own getKeyNames(). */
  public List<String> resolveKeyNames(Entity entity) {
    return !idFieldNames.isEmpty() ? idFieldNames : entity.getKeyNames();
  }

  /** Column name for a field, honoring {@code @Column(name)}; defaults to the field name. */
  public String columnOf(String fieldName) {
    String column = columnByField.get(fieldName);
    return column != null ? column : fieldName;
  }

  /** Field name for a result-set column label, honoring {@code @Column(name)} reverse lookup. */
  public String fieldForColumn(String columnLabel) {
    String fieldName = fieldByColumn.get(columnLabel.toLowerCase());
    return fieldName != null ? fieldName : columnLabel;
  }

  public boolean isTransient(String fieldName) {
    return transientFields.contains(fieldName);
  }

  /**
   * Whether the database generates the key on insert. With {@code @Id} declared, only an
   * accompanying {@code @GeneratedValue} makes it database-generated; without annotations
   * the legacy SystemIncrementEntity marker decides.
   */
  public boolean isDbGeneratedId(Entity entity) {

    if (!idFieldNames.isEmpty())
      return idGenerated;
    return !(entity instanceof SystemIncrementEntity);
  }

  /** Column name of the (first) id field, used to fetch the generated key on insert. */
  public String idColumnName() {

    if (idFieldNames.isEmpty())
      return "id";
    return columnOf(idFieldNames.get(0));
  }

  /**
   * Converts an enum field value for persistence: ORDINAL stores the ordinal integer,
   * STRING (or no annotation, the historical Modelet behavior) stores the name.
   */
  public Object convertEnumValue(String fieldName, Object enumValue) {

    if (enumValue == null)
      return null;
    if (EnumType.ORDINAL.equals(enumTypeByField.get(fieldName.toLowerCase())))
      return Integer.valueOf(((Enum<?>) enumValue).ordinal());
    return enumValue.toString();
  }

  /**
   * Restores a column value into an enum constant of enumClass: ordinal lookup for
   * {@code @Enumerated(ORDINAL)} fields, name lookup otherwise.
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public Object restoreEnumValue(Class enumClass, String fieldName, Object columnValue) {

    if (columnValue == null)
      return null;
    if (EnumType.ORDINAL.equals(enumTypeByField.get(fieldName.toLowerCase())) && (columnValue instanceof Number))
      return enumClass.getEnumConstants()[((Number) columnValue).intValue()];
    return Enum.valueOf(enumClass, columnValue.toString());
  }
}
