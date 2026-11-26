<#
.SYNOPSIS
Save a Word document as PDF, beside the document, using Microsoft Word.

.EXAMPLE
save-as-pdf.ps1 -Path .\document.docx
#>
param(
	[Parameter(Mandatory = $true)]
	[string]$Path
)

$documentPath = (Resolve-Path -LiteralPath $Path).Path
$pdfPath = [System.IO.Path]::ChangeExtension($documentPath, ".pdf")
# Word format constant for PDF
$wdFormatPDF = 17

$wordApp = New-Object -ComObject Word.Application
try {
	$doc = $wordApp.Documents.Open($documentPath)
	$doc.SaveAs([ref]$pdfPath, [ref]$wdFormatPDF)
	$doc.Close()
	Write-Output "Saved $pdfPath"
}
finally {
	$wordApp.Quit()
}
