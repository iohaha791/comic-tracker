' Double-click this to start Shelf with no console window.
' It opens your browser automatically once the server is up.
Set WshShell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")

strPath = fso.GetParentFolderName(WScript.ScriptFullName)
strPython = strPath & "\venv\Scripts\pythonw.exe"

If Not fso.FileExists(strPython) Then
    strPython = "pythonw.exe"
End If

WshShell.CurrentDirectory = strPath
WshShell.Run """" & strPython & """ launcher.py", 0, False
