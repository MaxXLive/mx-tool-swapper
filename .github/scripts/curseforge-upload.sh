#!/usr/bin/env bash
# Uploads a jar to a CurseForge project through the upload API.
#
# Environment:
#   CURSEFORGE_TOKEN    API token (https://legacy.curseforge.com/account/api-tokens)
#   CURSEFORGE_PROJECT  numeric project id
#   FILE                path of the jar
#   DISPLAY_NAME        name of the file on CurseForge, e.g. "MX Secondary Keys 26.3-patch-3"
#   CHANGELOG_FILE      markdown file with the changelog
#   MC_VERSION          Minecraft version, e.g. 26.3
#   ENVIRONMENTS        comma separated: Client, Server or Client,Server
#   JAVA_VERSION        e.g. "Java 25"
#   RELATIONS           JSON array of {"slug": ..., "type": requiredDependency|optionalDependency|...}
#
# The game version names are turned into the ids CurseForge expects, so nothing has to be looked up by hand
# when the Minecraft version changes.
set -euo pipefail

: "${CURSEFORGE_TOKEN:?}" "${CURSEFORGE_PROJECT:?}" "${FILE:?}" "${DISPLAY_NAME:?}" "${CHANGELOG_FILE:?}"
: "${MC_VERSION:?}" "${ENVIRONMENTS:?}" "${JAVA_VERSION:?}"
RELATIONS="${RELATIONS:-[]}"

api="https://minecraft.curseforge.com/api"
versions=$(curl -fsS -H "X-Api-Token: $CURSEFORGE_TOKEN" "$api/game/versions")
types=$(curl -fsS -H "X-Api-Token: $CURSEFORGE_TOKEN" "$api/game/version-types")

# id of the game version called $1 whose type slug matches the regex $2
version_id() {
	local id
	id=$(jq -r --arg name "$1" --arg re "$2" --argjson types "$types" '
		($types | map(select(.slug | test($re))) | map(.id)) as $type_ids
		| map(select(.name == $name and (.gameVersionTypeID as $t | $type_ids | index($t) != null)))
		| .[0].id // empty' <<<"$versions")
	if [[ -z "$id" ]]; then
		echo "CurseForge has no game version '$1' (type /$2/)" >&2
		exit 1
	fi
	echo "$id"
}

ids=("$(version_id "$MC_VERSION" '^minecraft-')" "$(version_id Fabric '^modloader$')" "$(version_id "$JAVA_VERSION" '^java$')")
IFS=',' read -r -a envs <<<"$ENVIRONMENTS"
for env in "${envs[@]}"; do
	ids+=("$(version_id "$env" '^environment$')")
done

metadata=$(jq -n \
	--arg name "$DISPLAY_NAME" \
	--rawfile changelog "$CHANGELOG_FILE" \
	--argjson versions "$(printf '%s\n' "${ids[@]}" | jq -s 'map(tonumber)')" \
	--argjson relations "$RELATIONS" \
	'{displayName: $name, changelog: $changelog, changelogType: "markdown", releaseType: "release",
	  gameVersions: $versions, relations: {projects: $relations}}')
echo "Uploading $FILE to CurseForge project $CURSEFORGE_PROJECT with game versions ${ids[*]}"

curl -sS --fail-with-body -H "X-Api-Token: $CURSEFORGE_TOKEN" \
	-F "metadata=$metadata" \
	-F "file=@$FILE" \
	"$api/projects/$CURSEFORGE_PROJECT/upload-file"
echo
