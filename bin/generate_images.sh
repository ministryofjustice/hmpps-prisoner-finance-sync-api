#!/bin/sh -e
root_dir="$(git rev-parse --show-toplevel)"
ext_dir="$root_dir/build/tmp"
exports_dir="$root_dir/docs/architecture/plantuml"
output_dir="$root_dir/docs/architecture/png"

mkdir -p "$ext_dir"
mkdir -p "$output_dir"

plantuml_version="1.2023.1"
plantuml_jar="$ext_dir/plantuml-nodot.${plantuml_version}.jar"
if [ ! -f "$plantuml_jar" ]; then
  echo
  echo "Downloading PlantUML..."
  wget "http://sourceforge.net/projects/plantuml/files/${plantuml_version}/plantuml-nodot.${plantuml_version}.jar/download" -O"$plantuml_jar"
fi

echo
echo "Generating images..."
(
  cd "$exports_dir"
  java -Djava.awt.headless=true -jar "$plantuml_jar" -o "$output_dir" -SmaxMessageSize=100 -tpng structurizr-*.puml
)
echo
echo "Cleaning images directory..."
(
  cd "$output_dir"
  rm structurizr-*-key.png
)
