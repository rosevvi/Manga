Option Explicit

' Keeps the Manga database SSH tunnel running without showing a console window.
Const SSH_EXECUTABLE = "C:\Windows\System32\OpenSSH\ssh.exe"
Const SSH_CONFIG_RELATIVE_PATH = "\.ssh\manga-db-tunnel.conf"
Const SSH_LOG_RELATIVE_PATH = "\.ssh\manga-db-tunnel.log"
Const SSH_HOST_ALIAS = "manga-db-tunnel"
Const HIDDEN_WINDOW = 0
Const WAIT_FOR_EXIT = True

Dim shell
Dim command
Dim userProfile

Set shell = CreateObject("WScript.Shell")
userProfile = shell.ExpandEnvironmentStrings("%USERPROFILE%")
command = Quote(SSH_EXECUTABLE) _
    & " -E " & Quote(userProfile & SSH_LOG_RELATIVE_PATH) _
    & " -F " & Quote(userProfile & SSH_CONFIG_RELATIVE_PATH) _
    & " -N -T " & SSH_HOST_ALIAS

WScript.Quit shell.Run(command, HIDDEN_WINDOW, WAIT_FOR_EXIT)

' Quotes Windows paths so spaces cannot split command arguments.
Function Quote(ByVal value)
    Quote = Chr(34) & value & Chr(34)
End Function
