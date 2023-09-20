# Update tables of content and figures from a word document
# Author: Iulian Rotaru
# Date: 05/09/2023

$documentPath = "D:\docs\workspaces\gnotis\prj\eon\md\script\work\document.docx"

# Create an instance of the Word application
$wordApp = New-Object -ComObject Word.Application

# Open the Word document
$doc = $wordApp.Documents.Open($documentPath)
#Write-Output $doc

# Update the table of contents
if ($doc.TablesOfContents.Count -gt 0) {
	Write-Output $doc.TablesOfContents
	Write-Output $doc.TablesOfContents.Item(1)
	$doc.TablesOfContents.Item(1).Update()
}

# Update the table of figures
if ($doc.TablesOfFigures.Count -gt 0) {
	Write-Output $doc.TablesOfFigures
	Write-Output $doc.TablesOfFigures.Item(1)
	$doc.TablesOfFigures.Item(1).Update()
}

# Save and close the document
$doc.Save()
$doc.Close()

# Quit the Word application
$wordApp.Quit()
