package modelet.model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import modelet.entity.AbstractEntity;
import modelet.entity.Entity;
import modelet.entity.EntityHelper;

import org.apache.commons.beanutils.MethodUtils;


public class ModelUtil {
  
  public static void setEntityKeyValue(AbstractEntity entity, String setter, Object newId) {
  
    try {
      MethodUtils.invokeMethod(entity, setter, newId);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  public static String makeSetter(String fieldName) {
    
    StringBuffer setter = new StringBuffer();
    setter.append("set").append(fieldName.substring(0, 1).toUpperCase()).append(fieldName.substring(1));
    return setter.toString();
  }

  public static String makeGetter(String fieldName) {
    
    StringBuffer setter = new StringBuffer();
    setter.append("get").append(fieldName.substring(0, 1).toUpperCase()).append(fieldName.substring(1));
    return setter.toString();
  }
  
  public static StatementSet buildPreparedCreateStatement(Entity entity) {
  	
  	StringBuffer stmt = new StringBuffer();
    stmt.append("INSERT INTO ").append(entity.getTableName()).append(" (");

    StringBuffer fieldNames = new StringBuffer();
    StringBuffer fieldMarks = new StringBuffer();
    List<Object> fieldValues = new ArrayList<Object>();
    Map<String, Object> fieldsAndValues = EntityHelper.convert(entity);
    Set<Map.Entry<String, Object>> entries = fieldsAndValues.entrySet();
    for (Iterator i=entries.iterator(); i.hasNext();) {
      Map.Entry entry = (Map.Entry) i.next();
      fieldNames.append(entry.getKey()).append(",");
      fieldMarks.append("?,");
      fieldValues.add(entry.getValue());
    }
    fieldNames.deleteCharAt(fieldNames.length()-1);
    fieldMarks.deleteCharAt(fieldMarks.length()-1);

    stmt.append(fieldNames).append(") VALUES (");
    stmt.append(fieldMarks).append(")");
    
    StatementSet statementSet = new StatementSet(stmt.toString(), fieldValues.toArray());
    return statementSet;
  }
  
  public static StatementSet buildPreparedUpdateStatement(Entity entity) {

    StringBuffer stmt = new StringBuffer();
    stmt.append("UPDATE ").append(entity.getTableName()).append(" set ");

    Map<String, Object> fieldsAndValues = EntityHelper.convert(entity);
    Set entries = fieldsAndValues.entrySet();
    List fieldValues = new ArrayList();
    for (Iterator i=entries.iterator(); i.hasNext();) {
      Map.Entry entry = (Map.Entry) i.next();
      stmt.append(entry.getKey()).append("=?,");
      fieldValues.add(entry.getValue());
    }
    stmt.deleteCharAt(stmt.length()-1);
    stmt.append(" where ");
    stmt.append(assembleKeyCriteria(entity, fieldValues));

    StatementSet statementSet = new StatementSet(stmt.toString(), fieldValues.toArray());
    return statementSet;
  }

  public static StatementSet buildPreparedDeleteStatement(Entity entity) {

    StringBuffer stmt = new StringBuffer();
    List keyValues = new ArrayList();
    stmt.append("DELETE FROM ").append(entity.getTableName()).append(" where ");
    stmt.append(assembleKeyCriteria(entity, keyValues));

    return new StatementSet(stmt.toString(), keyValues.toArray());
  }

  /**
   * Key values are appended to fieldValues and referenced with '?' placeholders, so they are always
   * bound as prepared statement parameters instead of being concatenated into the SQL text.
   */
  private static String assembleKeyCriteria(Entity entity, List fieldValues) {

    StringBuffer keyStmt = new StringBuffer();
    List keyEntries = entity.getKeyNames();
    for (Iterator k=keyEntries.iterator(); k.hasNext();) {
      String key = (String) k.next();
      keyStmt.append(key).append("=?");
      if (k.hasNext())
        keyStmt.append(" and ");
      fieldValues.add(getKeyParamValue(entity, key));
    }
    return keyStmt.toString();
  }

  private static Object getKeyParamValue(Entity entity, String key) {

    String methodName = makeGetter(key);
    try {
      Object rs = MethodUtils.invokeMethod(entity, methodName, null);
      if (rs instanceof Date)
        rs = new Timestamp(((Date) rs).getTime());
      else if (rs != null && rs.getClass().isEnum())
        rs = rs.toString();
      return rs;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
//  public static String buildCreateStatement(Entity entity) {
//
//    StringBuffer stmt = new StringBuffer();
//    stmt.append("INSERT INTO ").append(entity.getTableName()).append(" (");
//
//    StringBuffer fieldNames = new StringBuffer();
//    StringBuffer fieldValues = new StringBuffer();
//    Map<String, Object> fieldsAndValues = EntityHelper.convert(entity);
//    Set entries = fieldsAndValues.entrySet();
//    for (Iterator i=entries.iterator(); i.hasNext();) {
//      Map.Entry entry = (Map.Entry) i.next();
//      fieldNames.append(entry.getKey()).append(",");
//      fieldValues.append(entry.getValue()).append(",");
//    }
//    fieldNames.deleteCharAt(fieldNames.length()-1);
//    fieldValues.deleteCharAt(fieldValues.length()-1);
//
//    stmt.append(fieldNames).append(") VALUES (");
//    stmt.append(fieldValues).append(")");
//
//    return stmt.toString();
//  }
//
//  public static String buildUpdateStatement(Entity entity) {
//
//    StringBuffer stmt = new StringBuffer();
//    stmt.append("UPDATE ").append(entity.getTableName()).append(" set ");
//
//    Map<String, Object> fieldsAndValues = EntityHelper.convert(entity);
//    Set entries = fieldsAndValues.entrySet();
//    for (Iterator i=entries.iterator(); i.hasNext();) {
//      Map.Entry entry = (Map.Entry) i.next();
//      stmt.append(entry.getKey()).append("=").append(entry.getValue()).append(",");
//    }
//    stmt.deleteCharAt(stmt.length()-1);
//    stmt.append(" where ");
//    stmt.append(assembleKeyStatement(entity));
//    return stmt.toString();
//  }
  
  /**
   * @deprecated key values are concatenated into the SQL text, which is vulnerable to SQL injection
   * when a key value contains untrusted input. Use {@link #buildPreparedDeleteStatement(Entity)} instead.
   */
  @Deprecated
  public static String buildDeleteStatement(Entity entity) {

    StringBuffer stmt = new StringBuffer();
    try {

      stmt.append("DELETE FROM ").append(entity.getTableName()).append(" where ");
      stmt.append(assembleKeyStatement(entity));

    } catch (Exception e) {
      e.printStackTrace();
    }

    return stmt.toString();
  }

  /**
   * @deprecated see {@link #buildDeleteStatement(Entity)}; use {@link #assembleKeyCriteria(Entity, List)} instead.
   */
  @Deprecated
  private static String assembleKeyStatement(Entity entity) {
  
    StringBuffer keyStmt = new StringBuffer();
    List keyEntries = entity.getKeyNames();
    for (Iterator k=keyEntries.iterator(); k.hasNext();) {
      String key = (String) k.next();
      keyStmt.append(key).append("=").append(getKeyValue(entity, key)).append(" and ");
    }
    keyStmt.delete(keyStmt.lastIndexOf("and"), keyStmt.length());
    
    return keyStmt.toString();
  }
  
  private static String getKeyValue(Entity entity, String key) {
    
    String value = "";
    String methodName = "get" + key.substring(0, 1).toUpperCase() + key.substring(1);
    try {
      Object rs = MethodUtils.invokeMethod(entity, methodName, null);
      if (rs instanceof String)
        value = "'" + String.valueOf(rs) + "'";
      else
        value = String.valueOf(rs);
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
    return value;
  }

  
///**
// * �̾�entity ��setter �ұ���parameter type, ��newId�૬
// */
//private static Object testParameterType(AbstractEntity entity, String setter, Object newId) {
//	
//	Object classifiedId = newId;
//	Method[] methods = entity.getClass().getMethods();
//	for (int i=0; i<methods.length; i++) {
//		Method method = (Method) methods[i];
//		String methodName = method.getName();
//		if (methodName.equals(setter)) {
//			Class[] paramTypes = method.getParameterTypes();
//			if (paramTypes != null && paramTypes.length > 0) {
//				Class parameter = paramTypes[0];
//				if (parameter == String.class) {
//					classifiedId = String.valueOf(newId);
//				}
//				else if (parameter == long.class || parameter == Long.class) {
//					classifiedId = Long.valueOf(String.valueOf(newId));
//				}
//				else if (parameter == BigDecimal.class) {
//					classifiedId = new BigDecimal(String.valueOf(newId));
//				}
//				else if (parameter == int.class || parameter == Integer.class) {
//					classifiedId = Integer.valueOf(String.valueOf(newId));
//				}
//			}
//			break;
//		}
//	}
//	return classifiedId;
//}
}