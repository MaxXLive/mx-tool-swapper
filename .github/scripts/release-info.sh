#!/usr/bin/env bash
# Computes the next semver release version and changelog from git tags and commits.
#
# Versions follow v<mc>-<major>.<minor>.<patch>. Bump rules:
#   breaking change (! suffix or BREAKING CHANGE footer) → major
#   feat                                                  → minor
#   fix / perf / other functional                         → patch
# The Minecraft version comes from gradle.properties.
#
# Outputs (written to $GITHUB_OUTPUT when set, otherwise printed):
#   version    e.g. 26.3-1.1.0
#   tag        e.g. v26.3-1.1.0
#   mc         e.g. 26.3
#   previous   previous semver tag for this mc version (empty for the first release)
#   released   "true" if HEAD already carries a release tag
#   relevant   "true" if there are feat/fix/perf/breaking commits to release
#   bump       major | minor | patch
# The changelog is written to the file given as the first argument (default: CHANGELOG.md).
set -euo pipefail

changelog_file="${1:-CHANGELOG.md}"
mc_version=$(sed -n 's/^minecraft_version=//p' gradle.properties | tr -d '[:space:]')
if [[ -z "$mc_version" ]]; then
    echo "minecraft_version is missing in gradle.properties" >&2
    exit 1
fi

prefix="v${mc_version}-"
semver_re="^${prefix}[0-9]+\.[0-9]+\.[0-9]+$"

released=false
if git tag --points-at HEAD | grep -qE "$semver_re"; then
    released=true
fi

previous=$(git tag --list "${prefix}*" | grep -E "$semver_re" | sort -V | tail -n 1 || true)

# Changelog range: since the most recent release tag of any format or mc version
last_any=$(git describe --tags --abbrev=0 --match "v*-[0-9]*.[0-9]*.[0-9]*" 2>/dev/null \
    || git describe --tags --abbrev=0 --match "v*-patch-*" 2>/dev/null \
    || git describe --tags --abbrev=0 --match "v*-[0-9][0-9][0-9][0-9]" 2>/dev/null || true)
range="HEAD"
[[ -n "$last_any" ]] && range="${last_any}..HEAD"

features=()
fixes=()
other=()
bump=patch
while IFS=$'\t' read -r hash subject; do
    [[ -z "$hash" ]] && continue
    if [[ "$subject" =~ ^[a-z]+(\([^\)]*\))?!: ]] \
        || git show -s --format=%B "$hash" | grep -q "^BREAKING CHANGE"; then
        bump=major
    fi
    if [[ "$subject" =~ ^(feat|fix|perf|refactor|docs|build|ci|chore|test|style)(\([^\)]*\))?!?:[[:space:]]*(.*)$ ]]; then
        type="${BASH_REMATCH[1]}"
        text="${BASH_REMATCH[3]}"
    else
        type=""
        text="$subject"
    fi
    line="- ${text} (${hash})"
    case "$type" in
        feat) features+=("$line"); [[ "$bump" == patch ]] && bump=minor ;;
        fix | perf) fixes+=("$line") ;;
        docs | ci | test | style) ;;
        *) other+=("$line") ;;
    esac
done < <(git log "$range" --no-merges --format=$'%h\t%s')

if [[ -z "$previous" ]]; then
    version="${mc_version}-1.0.0"
else
    ver="${previous#$prefix}"
    major="${ver%%.*}"; rest="${ver#*.}"; minor="${rest%%.*}"; patch_n="${rest##*.}"
    case "$bump" in
        major) version="${mc_version}-$((major+1)).0.0" ;;
        minor) version="${mc_version}-${major}.$((minor+1)).0" ;;
        patch) version="${mc_version}-${major}.${minor}.$((patch_n+1))" ;;
    esac
fi
tag="v${version}"

{
    echo "Built for **Minecraft ${mc_version}** (Fabric, client only). Requires Fabric API and Cloth Config; Mod Menu is optional."
    echo
    if [[ -n "$last_any" && -n "${GITHUB_REPOSITORY:-}" ]]; then
        echo "**Full changelog:** ${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY}/compare/${last_any}...${tag}"
        echo
    fi
    if ((${#features[@]})); then
        echo "### New"
        printf '%s\n' "${features[@]}"
        echo
    fi
    if ((${#fixes[@]})); then
        echo "### Fixed"
        printf '%s\n' "${fixes[@]}"
        echo
    fi
    if ((${#other[@]})); then
        echo "### Other"
        printf '%s\n' "${other[@]}"
        echo
    fi
    if ((${#features[@]} + ${#fixes[@]} + ${#other[@]} == 0)); then
        echo "No functional changes."
        echo
    fi
} > "$changelog_file"

relevant=false
((${#features[@]} + ${#fixes[@]} + ${#other[@]} > 0)) && relevant=true

outputs=$(printf 'version=%s\ntag=%s\nmc=%s\nprevious=%s\nreleased=%s\nrelevant=%s\nbump=%s\n' \
    "$version" "$tag" "$mc_version" "$previous" "$released" "$relevant" "$bump")
[[ -n "${GITHUB_OUTPUT:-}" ]] && echo "$outputs" >> "$GITHUB_OUTPUT"
echo "$outputs"
