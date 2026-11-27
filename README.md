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
  document.properties    document properties, e.g. document.title, document.author
  revisions.md           revisions table
  document.md            document source, if neither source.dir nor source.files is set
  <source.dir>/*.md      chapter sources, merged in file name order, if source.dir is set
  <source.files>         chapter sources, merged in listed order, relative to source.dir if set
  *_<lang>.*             translations, e.g. document_ro.md, revisions_ro.md, document_ro.properties
```

`commons/src/main/java/com/jslib/md/DocumentSources.java` is the single place that resolves these files. See
`mdt docx --help`, `mdt xlsx --help` and `mdt translate --help` for the properties every command uses.

## Build

Requires Java 17 and Maven.

```
mvn install
```

The command line distribution is assembled into `build/`, with `bin/mdt.bat`, `conf/` and `lib/`. Add `build/bin` to
`PATH` to run `mdt` from any directory.
