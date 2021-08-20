package test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import org.junit.Ignore;
import org.junit.Test;

import modelet.entity.TxnMode;
import modelet.model.DefaultModel;
import modelet.model.Model;
import modelet.model.paging.PageContainer;
import modelet.model.paging.PagingElement;
import test.entity.Book;
import util.ApplicationContextHelper;

public class TestDefaultModel {
  
  private Model getModel() {
    
    DefaultModel model = ApplicationContextHelper.getBean("defaultModel");
    return model;
  }
  
  @Test
  @Ignore
  public void testQuery() {
    
    DefaultModel model = ApplicationContextHelper.getBean("defaultModel");
    Object[] params = {5L};
    List<Map<String, Object>> rs = model.find("select * from book where id > ? ", params);
    System.out.println(rs);
  }
  
  @Test
  @Ignore
  public void testPersist() {
    
    Book book = new Book();
    book.setBookName("Java學習筆記 第四版");
    book.setCreateDate(new Date());
    book.setPrice(new BigDecimal("150"));
    book.setAEnum(AEnum.A);
    
    DefaultModel model = ApplicationContextHelper.getBean("defaultModel");
    model.save(book);
    
    book.setAEnum(AEnum.B);
    
    model.save(book);
    
    Book myBook = model.getEntityById((Long)book.getId(), "book", Book.class);
    System.out.println(myBook.getBookName());
    
    Object[] params = {0L};
    List<Book> books = model.find("select * from book where id > ? ", params, Book.class);
    for (Book abook : books) {
      System.out.println(abook.getBookName());
    }
    
    List<Map<String, Object>> rows = model.find("select * from book where id > ? ", params);
    for (Map<String, Object> row : rows) {
      System.out.println(row.get("bookName") + "," + row.get("createDate"));
    }
    
    book.setTxnMode(TxnMode.DELETE);
    model.save(book);
  }
  
  @Test
  @Ignore
  public void testBulkInsert() {
    
    Model model = getModel();
    List<Book> books = new ArrayList<Book>();
    
    for (int i=0; i<10; i++) {
      
      Book book = new Book();
      book.setBookName("Java note book edition " + i);
      book.setCreateDate(new Date());
      book.setPrice(new BigDecimal("150"));
      book.setAEnum(AEnum.A);
      books.add(book);
    }
    
    model.save(books);
  }
  
  @Test
  public void testPaging() {
    
    Model model = getModel();
    
    PagingElement pagingElement = new PagingElement();
    pagingElement.setTargetPage(2);
    pagingElement.setRowsPerPage(2);
    
    PageContainer<SortedMap<String, Object>> pageContainer = (PageContainer<SortedMap<String, Object>>) model.findWithPaging("select * from book", null, pagingElement);
    System.out.println("Total pages=" + pageContainer.getTotalPages());
    System.out.println("Total records=" + pageContainer.getTotalRecords());
    for (Map<String, Object> row : pageContainer.getRows()) {
      System.out.println(row.get("bookName") + ", " + row.get("createDate"));
    }
  }
}
