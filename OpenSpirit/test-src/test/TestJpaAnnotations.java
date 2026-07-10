package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import modelet.entity.EntityMetadata;
import modelet.entity.TxnMode;
import modelet.model.ModelUtil;
import modelet.model.StatementSet;
import modelet.model.dataroller.EntityDataRoller;
import test.entity.JpaAccount;
import test.entity.JpaBook;

/**
 * Verifies the Jakarta Persistence annotation vocabulary (@Table, @Id, @Column,
 * @Transient, @Enumerated, @GeneratedValue) drives statement generation and
 * result-set mapping. Statement tests are pure; the mapping test runs on an
 * in-memory HSQLDB, no external database needed.
 */
public class TestJpaAnnotations {

  private static Connection cnct;

  @BeforeClass
  public static void openDb() throws Exception {
    Class.forName("org.hsqldb.jdbcDriver");
    //"jdbc:hsqldb:." is the pure in-memory URL of this HSQLDB generation; the
    //newer "mem:name" form would be treated as a file path and leave db files behind
    cnct = DriverManager.getConnection("jdbc:hsqldb:.", "sa", "");
  }

  @AfterClass
  public static void closeDb() throws Exception {
    cnct.close();
  }

  @Test
  public void tableAnnotationWinsOverConvention() {

    JpaBook book = new JpaBook();
    book.setTitle("annotated");
    StatementSet stmt = ModelUtil.buildPreparedCreateStatement(book);
    assertTrue(stmt.getSql(), stmt.getSql().startsWith("INSERT INTO book "));
  }

  @Test
  public void columnMappingAndTransientInInsert() {

    JpaBook book = new JpaBook();
    book.setTitle("annotated");
    book.setPrice(new BigDecimal("150"));
    book.setJoinedRowCount(Integer.valueOf(99)); //@Transient: must not appear

    StatementSet stmt = ModelUtil.buildPreparedCreateStatement(book);
    String sql = stmt.getSql();
    assertTrue(sql, sql.contains("bookName"));
    assertFalse(sql, sql.contains("title"));
    assertFalse(sql, sql.contains("joinedRowCount"));
  }

  @Test
  public void ordinalEnumStoredAsInteger() {

    JpaBook book = new JpaBook();
    book.setTitle("annotated");
    book.setGrade(AEnum.B);

    StatementSet stmt = ModelUtil.buildPreparedCreateStatement(book);
    int gradeIndex = indexOfColumn(stmt.getSql(), "gradeOrdinal");
    assertEquals(Integer.valueOf(AEnum.B.ordinal()), stmt.getParams()[gradeIndex]);
  }

  @Test
  public void idAnnotationDrivesWhereCriteria() {

    JpaAccount account = new JpaAccount();
    account.setAccountNo("A-001");
    account.setOwner("matt");
    account.setTxnMode(TxnMode.DELETE);

    StatementSet stmt = ModelUtil.buildPreparedDeleteStatement(account);
    assertEquals("DELETE FROM account where account_no=?", stmt.getSql());
    assertEquals("A-001", stmt.getParams()[0]);
  }

  @Test
  public void updateCriteriaUsesAnnotatedKeyWithPlaceholder() {

    JpaAccount account = new JpaAccount();
    account.setAccountNo("A'; DROP TABLE account;--");
    account.setOwner("mallory");

    StatementSet stmt = ModelUtil.buildPreparedUpdateStatement(account);
    String sql = stmt.getSql();
    assertTrue(sql, sql.endsWith(" where account_no=?"));
    assertFalse("key value must never be inlined into SQL", sql.contains("DROP TABLE"));
    assertEquals("A'; DROP TABLE account;--", stmt.getParams()[stmt.getParams().length - 1]);
  }

  @Test
  public void idWithoutGeneratedValueMeansApplicationSuppliedKey() {

    assertFalse(EntityMetadata.of(JpaAccount.class).isDbGeneratedId(new JpaAccount()));
    //JpaBook declares no @Id at all, so the legacy rule applies: db-generated
    assertTrue(EntityMetadata.of(JpaBook.class).isDbGeneratedId(new JpaBook()));
  }

  @Test
  public void resultSetMapsThroughColumnAndOrdinalEnum() throws Exception {

    Statement ddl = cnct.createStatement();
    ddl.execute("CREATE TABLE book (id INTEGER IDENTITY, bookName VARCHAR(100), price DECIMAL(10,2), gradeOrdinal INT, createDate TIMESTAMP)");
    ddl.execute("INSERT INTO book (bookName, price, gradeOrdinal) VALUES ('rolled', 150.00, " + AEnum.B.ordinal() + ")");

    ResultSet rst = ddl.executeQuery("SELECT * FROM book");
    assertTrue(rst.next());
    JpaBook book = new EntityDataRoller<JpaBook>(JpaBook.class).rollSingleRow(rst);
    rst.close();
    ddl.execute("DROP TABLE book");
    ddl.close();

    assertEquals("rolled", book.getTitle());
    assertEquals(new BigDecimal("150.00"), book.getPrice());
    assertEquals(AEnum.B, book.getGrade());
    assertNull(book.getJoinedRowCount());
    assertEquals(TxnMode.UPDATE, book.getTxnMode());
  }

  private int indexOfColumn(String insertSql, String columnName) {

    String columns = insertSql.substring(insertSql.indexOf('(') + 1, insertSql.indexOf(')'));
    return Arrays.asList(columns.split(",")).indexOf(columnName);
  }
}
