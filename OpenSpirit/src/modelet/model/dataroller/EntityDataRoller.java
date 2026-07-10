package modelet.model.dataroller;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import modelet.entity.Entity;
import modelet.entity.EntityMetadata;
import modelet.entity.TxnMode;
import modelet.model.paging.PageContainer;

import org.apache.commons.beanutils.MethodUtils;


public class EntityDataRoller<E extends Entity> extends DataRoller<E> {

  private Class clazz;
  
  public EntityDataRoller(Class clazz) {
    this.clazz = clazz;
  }
  
  public EntityDataRoller(Class clazz, PageContainer<E> pageContainer) {
    
    super(pageContainer);
    this.clazz = clazz;
  }

  @Override
  protected E processRow(List<Column> columns, ResultSet rst) throws Exception {

    E entity = (E) Class.forName(this.clazz.getName()).newInstance();
    EntityMetadata metadata = EntityMetadata.of(this.clazz);
    List<Method> publicSetterMethods = getPublicSetterMethods(this.clazz);
    for (Column column : columns) {
      Object columnValue = retrieveColumnValue(rst, column);
      if (columnValue == null)
        continue;
      //a column annotated with @Column(name) maps back to its field; otherwise the label itself is the property name
      String targetProperty = metadata.fieldForColumn(column.getName());
      for (Method method : publicSetterMethods) {
        if (method.getName().substring(3).equalsIgnoreCase(targetProperty)) {
          Class[] clazzs =  method.getParameterTypes();
          if (isSetterParamAEnum(method)) {
            columnValue = metadata.restoreEnumValue(clazzs[0], targetProperty, columnValue);
          }
          else if (isSetterParamAArray(method)) {
            columnValue = convertToArray(columnValue.toString(), clazzs[0]);
          }
          MethodUtils.invokeMethod(entity, method.getName(), columnValue);
          break;
        }
      }
    }
    entity.setTxnMode(TxnMode.UPDATE);
    return entity;
  }
  
  /**
   * 
   * @param <T>
   * @param columnValue
   * @param toType is a type of Array
   * @return
   */
  private Object convertToArray(String columnValue, Class toType) {
    
    Class componentType = toType.getComponentType();
    String[] values = columnValue.split(",");
    Object result = Array.newInstance(componentType, Array.getLength(values));
    for (int i = 0, icount = Array.getLength(values); i < icount; i++) {
      Object aenum = Enum.valueOf(componentType, values[i].trim());
      Array.set(result, i, aenum);
    }
    return result;
  }
  
  private boolean isSetterParamAArray(Method method) {
    
    Class[] clazzs =  method.getParameterTypes();
    if (clazzs.length != 1) {
      throw new RuntimeException("method setter should have at least one paramter.");
    }
    return clazzs[0].isArray();
  }
  
  private boolean isSetterParamAEnum(Method method) {
    
    Class[] clazzs =  method.getParameterTypes();
    if (clazzs.length != 1) {
      throw new RuntimeException("method setter should have at least one paramter.");
    }
    return clazzs[0].isEnum();
  }
  
  private List<Method> getPublicSetterMethods(Class clazz) {

    return getPublicMethods(clazz, "set");
  }

  private List<Method> getPublicGetterMethods(Class clazz) {

    return getPublicMethods(clazz, "get");
  }

  private List<Method> getPublicMethods(Class clazz, String getterSetter) {

    List<Method> publicMethods = new ArrayList<Method>();
    Method[] methods = clazz.getMethods();
    for (int i = 0; i < methods.length; i++) {
      Method method = methods[i];
      if (Modifier.isPublic(method.getModifiers()) && method.getName().startsWith(getterSetter))
        publicMethods.add(method);
    }

    return publicMethods;
  }


}
