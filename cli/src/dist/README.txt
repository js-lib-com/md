Markdown tools
==============

Generate Word documents and Excel workbooks from markdown sources.

Layout
  bin\mdt.bat   command line launcher
  conf\         configuration on class path, e.g. logging
  lib\          application and library jars

Setup
  Requires Java 11 or newer, from JAVA_HOME or PATH.
  Add the bin directory to PATH to run mdt from any directory.

Usage
  mdt docx [document-dir]        generate Word documents
  mdt xlsx [document-dir]        generate Excel workbook
  mdt translate [document-dir]   translate sources to Romanian using OpenAI
  mdt --help                     general help
  mdt <command> --help           command help, including project properties

Extra JVM options can be set with MDT_JAVA_OPTS environment variable.
