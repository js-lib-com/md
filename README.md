# Markdown tools

Command line tools, `mdt`, that generate Word documents and Excel workbooks from Markdown sources and translate
document sources using an OpenAI compatible chat completions API.

```
mdt docx [document-dir]        generate Word documents, one per language
mdt xlsx [document-dir]        generate an Excel workbook from Markdown tables
mdt translate [document-dir]   translate document sources
mdt <command> --help           command help, including project properties
```

## Modules

| Module      | Content                                                                                              |
|-------------|------------------------------------------------------------------------------------------------------|
| `commons`   | Shared by all commands: project properties, document directory layout, Markdown parser, inline HTML. |
| `docx`      | Word generator: Markdown visitor writing WordprocessingML into a DOCX template.                      |
| `xlsx`      | Excel generator: Markdown tables written as SpreadsheetML sheets, one per Markdown file.              |
| `translate` | Translator of document sources, using an OpenAI compatible API, e.g. a local llama.cpp server.       |
| `cli`       | `mdt` command line entry point and distribution: launcher, Word automation scripts, logging config.  |

Generators write OOXML directly; Apache POI is used only by tests, to read back generated files.

## Document directory

```
document-dir/
  project.properties     optional project properties, e.g. file.name, source.dir, source.files, languages
  document.properties    optional document properties, e.g. document.title, document.author
  revisions.md           optional revisions table
  document.md            document source, if neither source.dir nor source.files is set;
                         if missing, all *.md files are merged in natural file name order
  <source.dir>/*.md      chapter sources, merged in natural file name order, if source.dir is set
  <source.files>         chapter sources, merged in listed order, relative to source.dir if set
  *_<lang>.*             translations, e.g. document_ro.md, revisions_ro.md, document_ro.properties
```

All files are optional: `mdt docx` run in a directory with only Markdown files merges them into one document.

`commons/src/main/java/com/jslib/md/DocumentSources.java` is the single place that resolves these files. See
`mdt docx --help`, `mdt xlsx --help` and `mdt translate --help` for the properties every command uses.

## Defaults

Word documents get these values when the document properties do not set them; every inferred value is logged.

| Document property  | Default                                                                         |
|--------------------|---------------------------------------------------------------------------------|
| `document.title`   | Directory name, words capitalized, e.g. `user-guide` to "User Guide"            |
| `document.author`  | Author of the newest, i.e. last, revisions row, otherwise operating system user |
| `document.date`    | Date of the newest revisions row, otherwise today, as ISO date                  |
| other variables    | Empty; e.g. `document.version`, `document.subtitle`, `company.name`             |

A template text with a variable without value, missing or blank, is hidden together with its label, e.g. the title page
`Version` label when `document.version` is not set, or the footer subtitle dash when `document.subtitle` is not set.
Excel workbooks do not infer document properties: without author the workbook creator is `mdt`, and without an ISO
`document.date` the creation time is the generation time.

| Project property         | Default                                                                          |
|--------------------------|----------------------------------------------------------------------------------|
| `file.name`              | `document`                                                                       |
| `source.dir`             | Not set: `document.md`, or all `*.md` files from document directory              |
| `source.files`           | Not set: files merged in natural file name order, e.g. `2-x.md` before `10-x.md` |
| `languages`              | None, only the default language document is generated                            |
| `table.auto.fit`         | `true`: columns width computed from text quantity; set `false` to disable        |
| `table.column.min.width` | 10, in percent                                                                   |
| `table.column.outliers`  | 20, percent of cells ignored at each end                                         |
| `image.shadow`           | `false`                                                                          |
| `link.enabled`           | `false`                                                                          |
| `xlsx.table.width`       | 100 characters                                                                   |

## Build

Requires Java 17 and Maven.

```
mvn install
```

The command line distribution is assembled into `build/`, with `bin/mdt.bat`, `conf/` and `lib/`. Add `build/bin` to
`PATH` to run `mdt` from any directory.
