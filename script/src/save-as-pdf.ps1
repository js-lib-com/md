# Update save a word document as PDF
# Author: Iulian Rotaru
# Date: 05/09/2023

$workingDirPath = "D:\docs\workspaces\gnotis\prj\eon\md\script\work\"
$documentPath = $workingDirPath + "document.docx"
$pdfPath = $workingDirPath + "document.pdf"

# Create an instance of the Word application
$wordApp = New-Object -ComObject Word.Application

# Open the Word document
$doc = $wordApp.Documents.Open($documentPath)

# Save the document as PDF
$doc.SaveAs([ref]$pdfPath, [ref]17)  # 17 represents the PDF format constant

# Close the document
$doc.Close()

# Quit the Word application
$wordApp.Quit()
