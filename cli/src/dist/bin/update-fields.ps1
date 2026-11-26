<#
.SYNOPSIS
Update tables of contents and tables of figures of a Word document, using Microsoft Word.

.EXAMPLE
update-fields.ps1 -Path .\document.docx
#>
param(
	[Parameter(Mandatory = $true)]
	[string]$Path
)

$documentPath = (Resolve-Path -LiteralPath $Path).Path

$wordApp = New-Object -ComObject Word.Application
try {
	$doc = $wordApp.Documents.Open($documentPath)
	foreach ($toc in $doc.TablesOfContents) {
		$toc.Update()
	}
	foreach ($tof in $doc.TablesOfFigures) {
		$tof.Update()
	}
	$doc.Save()
	$doc.Close()
	Write-Output "Updated $documentPath"
}
finally {
	$wordApp.Quit()
}
