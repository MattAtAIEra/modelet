package modelet.entity;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import modelet.context.SessionContext;
import modelet.util.ReflactionUtil;

import org.apache.commons.beanutils.MethodUtils;


public class EntityHelper {
  
  public static Map<String, Object> convert(Entity entity) {

    Map<String, Object> value = new HashMap<String, Object>();
    EntityMetadata metadata = EntityMetadata.of(entity.getClass());

    List fields = new ArrayList();
    ReflactionUtil.retrieveFields(fields, entity.getClass());

    for (int i=0; i<fields.size(); i++) {
      Field field = (Field) fields.get(i);

      String fieldName = field.getName();

      if (metadata.isTransient(fieldName)) {
        continue;
      }

      if (fieldName.equalsIgnoreCase("id") && (entity.getId() != null)) {
        ; //no skip
      }
      else if (entity.getExclusiveFields().contains(fieldName)) {
        continue;
      }

      String prefix = "get";
//      Class fieldType = field.getType();
//      if (fieldType == boolean.class || fieldType == Boolean.class)
//        prefix = "is";
      
      String methodName = prefix + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
      try {
        Object fieldValue = MethodUtils.invokeMethod(entity, methodName, null);
        if (fieldValue != null && fieldValue.getClass().isArray()) {
          fieldValue = Arrays.toString((Object[])fieldValue);
          if (fieldValue.equals("null"))
            fieldValue = null;
          else 
            fieldValue = ((String)fieldValue).substring(1, ((String)fieldValue).length()-1);
        }
        
        if (fieldValue != null && fieldValue.getClass().isEnum()) {
          fieldValue = metadata.convertEnumValue(fieldName, fieldValue);
        }
        
//        if (fieldValue instanceof String) {
//          fieldValue = ((String)fieldValue).replaceAll("'", "''");
//        }
        else if ((fieldValue instanceof Calendar)) {
        	fieldValue = new Timestamp(((Calendar)fieldValue).getTimeInMillis());
        }
        else if ((fieldValue instanceof Date)) {
        	fieldValue = new Timestamp(((Date)fieldValue).getTime());
        }
        else if (fieldValue instanceof Boolean) {
          if (((Boolean)fieldValue).booleanValue())
            fieldValue = "1"; //true
          else
            fieldValue = "0"; //false
        }

        if (fieldValue != null || entity.isAllowNullValue()) {
          value.put(metadata.columnOf(fieldName), fieldValue);
        }
      }
      catch (Exception e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      }
    }
    return value;
  }
  
  public static void injectLoginInfo(Entity entity, SessionContext sessionContext) {
    
    TxnMode txnMode = entity.getTxnMode();
    if ((entity instanceof AppEntity) && (sessionContext != null) && (sessionContext.getLogin() != null)) {
      if (txnMode.equals(TxnMode.INSERT)) {
        ((AppEntity)entity).setCreateDate(new Date());
        ((AppEntity)entity).setCreator(sessionContext.getLogin().getLoginId());
      }
      ((AppEntity)entity).setModifyDate(new Date());
      ((AppEntity)entity).setModofier(sessionContext.getLogin().getLoginId());
    }
  }
}
