package modelet.model;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import modelet.context.Login;
import modelet.context.UserInfoHolder;
import modelet.entity.AppEntity;
import modelet.entity.Entity;
import modelet.entity.SystemIncrementEntity;
import modelet.entity.TxnMode;
import modelet.model.dataroller.DataRoller;
import modelet.model.dataroller.EntityDataRoller;
import modelet.model.dataroller.MapDataRoller;
import modelet.model.paging.DefaultPageContainer;
import modelet.model.paging.PageContainer;
import modelet.model.paging.PagingElement;

@Component("defaultModel")
@Transactional(readOnly = true)
@Primary
public class DefaultModel implements Model {

	private static final Log LOG = LogFactory.getLog(DefaultModel.class);
  private static final Log EXP_LOG = LogFactory.getLog("exceptionLog");
  
	private DataSource dataSource;
	private JdbcTemplate jdbcTemplate;

	private boolean txnSuccessful = true;
  private String txnErrorStack = null;
  private boolean swallowException = false;
  
  public boolean isSwallowException() {
    return swallowException;
  }
  
  public void setSwallowException(boolean swallowException) {
    this.swallowException = swallowException;
  }

  public String getTxnErrorStack() {
		return txnErrorStack;
	}

	private void setTxnErrorStack(String txnErrorStack) {
		this.txnErrorStack = txnErrorStack;
	}

	public boolean isTxnSuccessful() {
		return txnSuccessful;
	}

	private void setTxnSuccessful(boolean txnSuccessful) {
		this.txnSuccessful = txnSuccessful;
	}

	@Transactional(readOnly = false)
  public <E extends Entity> void save(List<E> entities) throws ModelException {
  	
  	for (E e : entities) {
  		save(e);
  	}
  }
  
  @Transactional(readOnly = false)
  public int save(Entity entity) throws ModelException {
  	
  	TxnMode txnMode = entity.getTxnMode();
  	if (txnMode == null)
  		throw new ModelException("Please assign action type(insert, update, delete) before persist entity to database.");
  	
  	int returnCode = 0;
  	injectSignatureAndTimestamp(entity);
  	entity.beforeSave();
  	if (txnMode.equals(TxnMode.INSERT))
  	  returnCode = insert(entity);
  	else if (txnMode.equals(TxnMode.UPDATE))
  	  returnCode = update(entity);
  	else if (txnMode.equals(TxnMode.DELETE))
  	  returnCode = delete(entity);
  	
  	entity.afterSave();
  	return returnCode;
  }

  private void injectSignatureAndTimestamp(Entity entity) {
    
    if (entity instanceof AppEntity) {
      
      AppEntity appEntity = (AppEntity) entity;
      TxnMode txnMode = entity.getTxnMode();
      if (txnMode.equals(TxnMode.INSERT)) {
        appEntity.setCreateDate(new Date());
        injectUserInfo(appEntity);
      }
      else {
        appEntity.setModifyDate(new Date());
        injectUserInfo(appEntity);
      }
    }
  }
  
  private void injectUserInfo(AppEntity appEntity) {
    
    Login userInfo = UserInfoHolder.get();
    if (userInfo != null) {
      TxnMode txnMode = appEntity.getTxnMode();
      if (txnMode.equals(TxnMode.INSERT)) {
        appEntity.setCreator(userInfo.getLoginId());
      }
      else {
        appEntity.setModofier(userInfo.getLoginId());
      }
    }
  }
  
	private int insert(final Entity entity) {

	  int returnCode = 0;
		final StatementSet stmtSet = ModelUtil.buildPreparedCreateStatement(entity);
    try {
    	if (entity instanceof SystemIncrementEntity) {
    		returnCode = jdbcTemplate.update(stmtSet.getSql(), stmtSet.getParams());
    	}
    	else if (entity.getId() != null) {
    	  returnCode = executeStatementSet(stmtSet);
    	}
    	else {
	      KeyHolder keyHolder = new GeneratedKeyHolder();
	      returnCode = jdbcTemplate.update(new PreparedStatementCreator() {
	
	        public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
	        	
	          LOG.info("Model INFO :" + stmtSet.getSql() + " param : " + Arrays.toString(stmtSet.getParams()));
	          PreparedStatement ps = connection.prepareStatement(stmtSet.getSql(), new String[] { "id" });
	          appendParam(ps, stmtSet.getParams());
	          return ps;
	        }
	      }, keyHolder);
	
	      long keyValue = keyHolder.getKey().longValue();
	      entity.setId(keyValue);
    	}
    	LOG.info("INSERT returnCode : [" + returnCode + "]");
      entity.setTxnMode(TxnMode.UPDATE);
      
    }
    catch (DataAccessException e) {
    	e.printStackTrace();
    	setTxnSuccessful(false);
    	logSqlError(e, stmtSet.getSql(), stmtSet.getParams());
    	logExceptionStack(e);
    	throw new ModelException("Error occured while inserting data!", e);
    }
    return returnCode;
  }
	
	private int update(Entity entity) {
		
		StatementSet stmtSet = ModelUtil.buildPreparedUpdateStatement(entity);
		return executeStatementSet(stmtSet);
	}
	
	private int executeStatementSet(StatementSet stmtSet) {
	  
	  int returnCode = 0;
	  try {
	    LOG.info("Model INFO :" + stmtSet.getSql() + " param : " + Arrays.toString(stmtSet.getParams()));
      returnCode = jdbcTemplate.update(stmtSet.getSql(), stmtSet.getParams());
      LOG.info("UPDATE returnCode : [" + returnCode + "]");
    }
    catch (DataAccessException e) {
      e.printStackTrace();
      setTxnSuccessful(false);
      logSqlError(e, stmtSet.getSql(), stmtSet.getParams());
      logExceptionStack(e);
      throw new ModelException("Error occured while updaeing data!", e);
    }
    return returnCode;
	}
	
	private int delete(Entity entity) {
		
	  int returnCode = 0;
		String sql = ModelUtil.buildDeleteStatement(entity);
		try {
			returnCode = jdbcTemplate.update(sql);
			LOG.info("DELETE returnCode : [" + returnCode + "]");
		}
		catch (DataAccessException e) {
    	e.printStackTrace();
    	setTxnSuccessful(false);
    	logSqlError(e, sql);
    	logExceptionStack(e);
    	throw new ModelException("Error occured while updaeing data!", e);
    }
		return returnCode;
	}
	
	public <E extends Entity> E findOne(String sql, Object[] params, Class<E> clazz) {
	
    try {
      E entity = (E) Class.forName(clazz.getName()).newInstance();
  	  List<E> entities = find(sql, params, clazz);
  	  if (entities != null && entities.size() > 0) {
  	    entity = entities.get(0);
  	  }
  	  return entity;
    } catch (Exception e) {
      throw new ModelException("Can not create instance of " + clazz.getName(), e);
    }
	}
	
	@SuppressWarnings("unchecked")
  public List<Map<String, Object>> find(String sql, Object[] params) {
	  
	  RowMapper rowMapper = new RowMapper() {
      
      public Object mapRow(ResultSet rs, int rowNum) throws SQLException {
        
        MapDataRoller roller = new MapDataRoller();
        Map<String, Object> row = roller.rollSingleRow(rs);
        return row;
      }
      
    };
    convertParams(params);
    List<Map<String, Object>> entities = jdbcTemplate.query(sql, params, rowMapper);
    return entities;
    
//	  MapListHandler rsHandler = new MapListHandler();
//	  List<Map<String, Object>> entities = new ArrayList<Map<String, Object>>();
//	  try {
//      convertDateIn(params);
//      entities = (List<Map<String, Object>>) getQueryRunner().query(sql, params, rsHandler);
//    }
//    catch (SQLException e) {
//      e.printStackTrace();
//      logSqlError(e, sql, params);
//      logExceptionStack(e);
//    }
//    return entities;
	}
	
	@SuppressWarnings("unchecked")
  public <E extends Entity> List<E> find(String sql, Object[] params, final Class<E> clazz) {
    
	  RowMapper rowMapper = new RowMapper() {
	    
      public Object mapRow(ResultSet rs, int rowNum) throws SQLException {
        
        EntityDataRoller<E> roller = new EntityDataRoller<E>(clazz);
        E e = roller.rollSingleRow(rs);
//        e.setTxnMode(TxnMode.UPDATE);
        return e;
      }
	    
	  };
	  convertParams(params);
	  List<E> entities = jdbcTemplate.query(sql, params, rowMapper);
	  return entities;
	  
//    ResultSetHandler rsHandler = new BeanListHandler(clazz);
//    List<E> entities = new ArrayList<E>(0);
//    try {
//    	convertDateIn(params);
//    	entities = (List<E>) getQueryRunner().query(sql, params, rsHandler);
//    	for (Entity e: entities) {
//    		e.setTxnMode(TxnMode.UPDATE);
//    	}
//    }
//    catch (SQLException e) {
//    	e.printStackTrace();
//    	logSqlError(e, sql, params);
//    	logExceptionStack(e);
//    }
//    return entities;
  }

	public PageContainer<SortedMap<String, Object>> findWithPaging(String sql, Object[] params, final PagingElement pagingElement) throws ModelException {
    
    PageContainer<SortedMap<String, Object>> pageContainer = null;
    try {
      
      RstHandler<PageContainer<SortedMap<String, Object>>> rstHandler = new RstHandler<PageContainer<SortedMap<String, Object>>>() {

        @SuppressWarnings("unchecked")
        @Override
        PageContainer<SortedMap<String, Object>> handleRst(ResultSet rst) {
          
          final PageContainer<SortedMap<String, Object>> pageContainer = new DefaultPageContainer<SortedMap<String,Object>>();
          DataRoller dataRoller = new MapDataRoller(pageContainer);
          dataRoller.roll(rst, pagingElement);
          return pageContainer;
        }
        
      };
      
      pageContainer = executeSql(sql, params, rstHandler);
    }
    catch (SQLException e) {
      EXP_LOG.error("Fail to execute query: " + sql + " param : " + Arrays.toString(params), e);
      throw new ModelException("Fail to execute query", e);
    }
    return pageContainer;
  }

  public PageContainer<SortedMap<String, Object>> findWithPaging(String sql, Object[] params, final int page, final int rowsPerPage) throws ModelException {
    
    PageContainer<SortedMap<String, Object>> pageContainer = null;
    try {
      
      RstHandler<PageContainer<SortedMap<String, Object>>> rstHandler = new RstHandler<PageContainer<SortedMap<String, Object>>>() {

        @SuppressWarnings("unchecked")
        @Override
        PageContainer<SortedMap<String, Object>> handleRst(ResultSet rst) {
          
          final PageContainer<SortedMap<String, Object>> pageContainer = new DefaultPageContainer<SortedMap<String,Object>>();
          DataRoller dataRoller = new MapDataRoller(pageContainer);
          dataRoller.roll(rst, page, rowsPerPage);
          return pageContainer;
        }
        
      };
      
      pageContainer = executeSql(sql, params, rstHandler);
    }
    catch (SQLException e) {
      EXP_LOG.error("Fail to execute query: " + sql + " param : " + Arrays.toString(params), e);
      throw new ModelException("Fail to execute query", e);
    }
    return pageContainer;
  }
  
	public <E extends Entity> PageContainer<E> findWithPaging(String sql, Object[] params, final Class<E> clazz, final PagingElement pagingElement) throws ModelException {
    
    PageContainer<E> pageContainer = new DefaultPageContainer<E>();
    try {
      RstHandler<PageContainer<E>> rstHandler = new RstHandler<PageContainer<E>>() {

        @SuppressWarnings("unchecked")
        @Override
        PageContainer<E> handleRst(ResultSet rst) {
          
          final PageContainer<E> pageContainer = new DefaultPageContainer<E>();
          DataRoller dataRoller = new EntityDataRoller<E>(clazz, pageContainer);
          dataRoller.roll(rst, pagingElement);
          return pageContainer;
        }
        
      };
      
      pageContainer = executeSql(sql, params, rstHandler);
    }
    catch (SQLException e) {
      EXP_LOG.error("Fail to execute query: " + sql + " param : " + Arrays.toString(params), e);
      throw new TransactionRollbackedException("Fail to execute query", e);
    }
    return pageContainer;
  }

  public <E extends Entity> PageContainer<E> findWithPaging(String sql, Object[] params, final Class<E> clazz, final int page, final int rowsPerPage) throws ModelException {
    
    PageContainer<E> pageContainer = new DefaultPageContainer<E>();
    try {
      RstHandler<PageContainer<E>> rstHandler = new RstHandler<PageContainer<E>>() {

        @SuppressWarnings("unchecked")
        @Override
        PageContainer<E> handleRst(ResultSet rst) {
          
          final PageContainer<E> pageContainer = new DefaultPageContainer<E>();
          DataRoller dataRoller = new EntityDataRoller<E>(clazz, pageContainer);
          dataRoller.roll(rst, page, rowsPerPage);
          return pageContainer;
        }
        
      };
      
      pageContainer = executeSql(sql, params, rstHandler);
    }
    catch (SQLException e) {
      EXP_LOG.error("Fail to execute query: " + sql + " param : " + Arrays.toString(params), e);
      throw new TransactionRollbackedException("Fail to execute query", e);
    }
    return pageContainer;
  }
  
  public <T extends Entity> T getEntityById(Long id, String tableName, Class<T> clazz) {
    
    if (id == null) {
      id = 0L;
    }

    StringBuffer sql = new StringBuffer();
    sql.append(" select * from ").append(tableName).append(" where id=?");
    Object[] params = {id};
    T rs = findOne(sql.toString(), params, clazz);
    return rs;
  }
  
  
  public Map<String, Object> getEntityMapById(Long id, String tableName) {
    
    if (id == null) {
      id = 0L;
    }

    Map<String, Object> entityMap = new HashMap<String, Object>();
    StringBuffer sql = new StringBuffer();
    sql.append(" select * from ").append(tableName).append(" where id=?");
    Object[] params = {id};
    List<Map<String, Object>> rs = find(sql.toString(), params);
    if (rs.size() > 0) {
      entityMap = rs.get(0);
    }
    return entityMap;
  }
  
  @Transactional(readOnly=false)
  public int executeSql(String sql, Object[] params) throws ModelException {
    
    int count = 0;
    try {
      convertParams(params);
//      for (int i=0; params != null && (i<params.length); i++) {
//        Object obj = params[i];
//        if (obj instanceof Date) {
//          obj = new Timestamp(((Date)obj).getTime());
//          params[i] = obj;
//        }
//        else if (obj.getClass().isEnum()) {
//          obj = obj.toString();
//          params[i] = obj;
//        }
//      }
      LOG.info("Model INFO :" + sql + " param : " + Arrays.toString(params));
      count = jdbcTemplate.update(sql, params);
      LOG.info("sql execution return count : [" + count + "]");
    }
    catch (DataAccessException e) {
      EXP_LOG.error("Fail to execute statement: " + sql + " param : " + Arrays.toString(params), e);
      throw new ModelException("Fail to execute update sql", e);
    }
    return count;
  }
  
	//_Resource(name = "defaultDataSource")
  @Autowired
	public void setDataSource(@Qualifier("defaultDataSource") DataSource dataSource) {
	  this.dataSource = dataSource;
	}
	
	//_Resource(name = "jdbcTemplate")
  @Autowired
	public void setJdbcTemplate(@Qualifier("jdbcTemplate") JdbcTemplate jdbcTemplate) {
	  this.jdbcTemplate = jdbcTemplate;
	}
	
	protected DataSource getDataSource() {
		return dataSource;
	}

	protected JdbcTemplate getJdbcTemplate() {
		return jdbcTemplate;
	}

  private <A> A executeSql(String sql, Object[] params, RstHandler<A> handler) throws SQLException  {
  	
  	A rs = null;
  	Connection cnct = getDataSource().getConnection();
  	try {
	    PreparedStatement stmt = cnct.prepareStatement(sql);
	    convertParams(params);
	    for (int i=0; params != null && (i<params.length); i++) {
	      stmt.setObject(i+1, params[i]);
	    }
	    ResultSet rst = stmt.executeQuery();
	    rs = handler.handleRst(rst);
	    rst.close();
	    stmt.close();
  	}
  	catch (SQLException e) {
  		e.printStackTrace();
  		throw e;
  	}
  	finally {
  	  cnct.close();
  	}
    return rs;
	}
  
	private void convertParams(Object[] params) {
	
	  for (int i=0; params != null && (i<params.length); i++) {
      Object obj = params[i];
      if (obj instanceof Date) {
        obj = new Timestamp(((Date)obj).getTime());
        params[i] = obj;
      }
      else if (obj instanceof Boolean) {
        if (((Boolean)obj).booleanValue())
          params[i] = "1"; //true
        else
          params[i] = "0"; //false
      }
      else if (obj.getClass().isEnum()) {
        obj = obj.toString();
        params[i] = obj;
      }
    }
	}
	
  abstract class RstHandler<A> {
  	abstract A handleRst(ResultSet rst);
  }
	
	private void appendParam(PreparedStatement ps, Object param[]) throws SQLException {

    int i = 1;
    for (Object value : param) {
      if (value == null)
        ps.setNull(i, java.sql.Types.NULL);
      else if (value instanceof String)
        ps.setString(i, (String) value);
      else if (value instanceof Integer)
        ps.setInt(i, (Integer) value);
      else if (value instanceof Long)
        ps.setLong(i, (Long) value);
      else if (value instanceof BigDecimal)
      	ps.setBigDecimal(i, (BigDecimal) value);
      else if (value instanceof Timestamp)
      	ps.setTimestamp(i, (Timestamp) value);
      else
        ps.setObject(i, value);
      i++;
    }
  }
	
	private Object[] convertDateIn(Object[] params) {
  	
  	for (int i=0; params != null && (i<params.length); i++) {
    	Object obj = params[i];
    	if (obj instanceof Date) {
    		obj = new Timestamp(((Date)obj).getTime());
    		params[i] = obj;
    	}
    }
  	return params;
  }
	
	private void logSqlError(Exception e, String sql, Object... params) {
  	
  	StringBuffer paramsContent = new StringBuffer();
		for (int i=0; i<params.length; i++) {
			Object aparam = params[i];
			if (aparam == null)
				aparam = "[null]";
			paramsContent.append(aparam.toString()).append("  ");
		}
  	EXP_LOG.error("Fail to execute: " + sql + ", params: " + paramsContent.toString(), e);
  }
	
	private void logExceptionStack(Exception e) {
		
		StringBuffer content = new StringBuffer();
		StackTraceElement[] trace = e.getStackTrace();
  	if (trace != null) {
  		for (int i=0; i<trace.length; i++) {
  			content.append(trace[i].getClassName() + " : " + trace[i].getMethodName() + " : " + trace[i].getLineNumber()).append("\n");
  		}
  	}
  	setTxnErrorStack(content.toString());
	}

}
