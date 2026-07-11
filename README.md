# Modelet

SQL-first micro ORM, born in 2006. Queries are written in the plain ANSI SQL
you already know (subqueries, joins, anything); the framework only takes over
the mechanical INSERT / UPDATE / DELETE work, driven by each entity's
transaction-mode state machine.

查詢交給 SQL,寫入交給框架。工程師只要會 ANSI SQL 就能上手,繁瑣的
INSERT / UPDATE / DELETE 由框架代勞。

**Website:** https://mattataiera.github.io/modelet/ ·
**Python port:** [pymodelet](https://github.com/MattAtAIEra/pymodelet) —
the same philosophy on DB-API 2.0 with dataclass entities.

The story behind the 2006 decision — a tribute:
[English](TRIBUTE.md) · [繁體中文](TRIBUTE.zh.md) · [日本語](TRIBUTE.ja.md) ·
[Deutsch](TRIBUTE.de.md) · [한국어](TRIBUTE.ko.md).

## Repository layout

| Path | Description |
|------|-------------|
| `OpenSpirit/` | The Java framework (2006–), built on Spring JDBC. Produces `modelet-3.0.x.jar` via `ant core-jar`. |
| `docs/` · `site-src/` | The project website (generated — see below). |

## Jakarta Persistence vocabulary

Since Modelet 3.0, entities can be declared with the Jakarta Persistence
annotation vocabulary — `@Table`, `@Id`, `@Column`, `@Transient`,
`@Enumerated`, `@GeneratedValue`. Only the vocabulary is borrowed: the engine
stays SQL-first (no JPQL, no EntityManager, no proxies, no session cache), and
every annotation falls back to the original convention methods, so pre-3.0
entities keep working unchanged.

```java
@Table(name = "book")
public class Book extends AbstractEntity {

  @Column(name = "bookName")
  private String title;

  @Enumerated(EnumType.ORDINAL)
  private Grade grade;

  @Transient
  private Integer joinedRowCount;
  // getters/setters ...
}
```

Usage:

```java
Book book = new Book();
book.setTitle("Java note book");
model.save(book);                                  // INSERT, id written back

List<Book> books = model.find(
    "select * from book where id > ?", new Object[]{5L}, Book.class);

book.setTxnMode(TxnMode.DELETE);
model.save(book);
```

## Website

The GitHub Pages site (`docs/`, served at
https://mattataiera.github.io/modelet/) is **generated** — do not edit it
directly. Edit the templates in `site-src/` (text lives in each page's I18N
dictionary), then rebuild:

```bash
node scripts/build-i18n.mjs
```

This pre-renders every page in five languages (`/` English, `/zh/`, `/ja/`,
`/de/`, `/ko/`) with translated titles and meta descriptions, canonical +
hreflang links, plus `sitemap.xml` and `robots.txt` — one crawlable URL per
language, as multilingual SEO requires.

## Testing

Java unit tests live in `OpenSpirit/test-src` (JUnit 4; most cases need a
configured database, see `OpenSpirit/config/spring/core.properties`).
