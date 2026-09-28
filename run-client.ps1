param([string]$Name = "")
$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($Name)) {
    mvn -Pclient -DskipTests javafx:run
} else {
    mvn -Pclient -DskipTests "-Dclient.args=--name=$Name" javafx:run
}
