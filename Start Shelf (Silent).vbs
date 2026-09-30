' Put a shortcut to this file in shell:startup instead of Start Shelf.vbs.
' It starts the server hidden at login WITHOUT opening a browser tab.
' Go to http://127.0.0.1:5050 yourself whenever you actually want it.
Set WshShell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")

strPath = fso.GetParentFolderName(WScript.ScriptFullName)
strPython = strPath & "\venv\Scripts\pythonw.exe"

If Not fso.FileExists(strPython) Then
    strPython = "pythonw.exe"
End If

WshShell.CurrentDirectory = strPath
WshShell.Run """" & strPython & """ launcher.py --no-browser", 0, False
