#!/usr/bin/env bash
# Publish the Maven artifact to the static Maven host (the
# modaal-agent/maven repository, served at
# https://modaal-agent.github.io/maven).
#
#   scripts/publish-maven.sh <version> <host-checkout> [--commit]
#
# Called by .github/workflows/publish.yml on a tag push (version = the tag),
# and runnable locally against a checkout of the host repository for a
# rehearsal or a manual publish. Three properties, asserted in order:
#
#   1. VERSION IS THE TAG. The build is invoked with -PpublishVersion=<version>
#      so the published coordinate cannot lag a hand-moved literal.
#   2. A RELEASE IS ATOMIC. All coordinates below must be staged before
#      anything reaches the host.
#   3. A PUBLISHED VERSION IS IMMUTABLE. If the host already carries the FULL
#      coordinate set for this version, the publish is a no-op (safe re-run:
#      the workflow may fire again on the same tag). If it carries PART of the
#      set, something is wrong on the host — fail without writing. Nothing
#      ever overwrites an existing <group>/<artifact>/<version>/ path; a bad
#      release is followed by a new version.
#
# `maven-metadata.xml` per artifact is DERIVED data (the version listing) and
# is regenerated from the host tree on every publish — the one deliberate
# exception to the no-overwrite rule. Fixed-version consumers resolve by pure
# path GET and never read it; it exists for tooling that lists versions.
set -euo pipefail

VERSION="${1:?usage: publish-maven.sh <version> <host-checkout> [--commit]}"
HOST="${2:?usage: publish-maven.sh <version> <host-checkout> [--commit]}"
COMMIT="${3:-}"

if [[ "$VERSION" == *-SNAPSHOT ]]; then
  echo "publish-maven: refusing to publish a -SNAPSHOT ($VERSION) — snapshots are the mavenLocal development flow." >&2
  exit 2
fi
if [[ ! -d "$HOST/.git" ]]; then
  echo "publish-maven: $HOST is not a git checkout of the host repository." >&2
  exit 2
fi

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STAGING="$REPO_DIR/build/staging-maven"
GROUP_PATH="dev/modaal"

# The publication set. Growing it (a new module) means growing this list in
# the same change — the atomicity assertion is the reminder.
COORDINATES=(
  mocks-processor
)

# PUBLISH_MAVEN_SKIP_STAGE=1 asserts/publishes the staging tree AS IT STANDS
# instead of rebuilding it — for the red controls (delete a staged coordinate,
# assert the atomicity check fires) and for a rehearsal that just staged.
if [[ "${PUBLISH_MAVEN_SKIP_STAGE:-}" != "1" ]]; then
  echo "publish-maven: staging $VERSION" >&2
  rm -rf "$STAGING"
  (cd "$REPO_DIR" && ./gradlew publishAllPublicationsToStagingRepository \
    -PpublishVersion="$VERSION" --console=plain -q)
fi

# 2. Atomicity: every coordinate staged, each with a POM for the version.
missing=0
for artifact in "${COORDINATES[@]}"; do
  pom="$STAGING/$GROUP_PATH/$artifact/$VERSION/$artifact-$VERSION.pom"
  if [[ ! -f "$pom" ]]; then
    echo "publish-maven: MISSING staged coordinate: $artifact ($pom)" >&2
    missing=1
  fi
done
if [[ "$missing" != "0" ]]; then
  echo "publish-maven: the staged tree is not a complete release — nothing was written to the host." >&2
  exit 1
fi
extra="$(cd "$STAGING/$GROUP_PATH" && ls -d */ | tr -d '/' | grep -vxF -f <(printf '%s\n' "${COORDINATES[@]}") || true)"
if [[ -n "$extra" ]]; then
  echo "publish-maven: staged coordinates not in the publication list: $extra" >&2
  echo "  (a new module must be added to COORDINATES in this script)" >&2
  exit 1
fi
file_count="$(find "$STAGING/$GROUP_PATH" -type f | wc -l | tr -d ' ')"
echo "publish-maven: staged ${#COORDINATES[@]} coordinates, $file_count files" >&2

# 3. Immutability: never overwrite; full-set re-run is a no-op.
present=0
for artifact in "${COORDINATES[@]}"; do
  [[ -d "$HOST/$GROUP_PATH/$artifact/$VERSION" ]] && present=$((present + 1))
done
if [[ "$present" -eq "${#COORDINATES[@]}" ]]; then
  echo "publish-maven: $VERSION is already fully published on the host — no-op." >&2
  exit 0
fi
if [[ "$present" -gt 0 ]]; then
  echo "publish-maven: $VERSION is PARTIALLY present on the host ($present of ${#COORDINATES[@]} coordinates)." >&2
  echo "  A published version is immutable and a release is atomic — this state should not exist." >&2
  echo "  Inspect the host tree; if the partial set is bad, cut a NEW version rather than overwriting." >&2
  exit 1
fi

for artifact in "${COORDINATES[@]}"; do
  mkdir -p "$HOST/$GROUP_PATH/$artifact"
  cp -R "$STAGING/$GROUP_PATH/$artifact/$VERSION" "$HOST/$GROUP_PATH/$artifact/$VERSION"
done

# maven-metadata.xml per artifact: regenerated from the version directories
# actually on the host (derived data — see the header). Only THIS group
# path's artifacts are touched; other groups on the host are other
# publishers' trees.
python3 - "$HOST/$GROUP_PATH" <<'PY'
import hashlib
import pathlib
import re
import sys
import time

group_dir = pathlib.Path(sys.argv[1])
group_id = "dev.modaal"

def version_key(v: str):
    return [int(x) for x in re.findall(r"\d+", v)]

# Restrict to the artifacts this repository publishes: the bare dev/modaal
# path is a PREFIX of sibling group paths (dev/modaal/duet/...), whose
# directories must not be mistaken for artifacts of this group.
artifacts = {"mocks-processor"}

for artifact_dir in sorted(p for p in group_dir.iterdir() if p.is_dir() and p.name in artifacts):
    versions = sorted(
        (d.name for d in artifact_dir.iterdir() if d.is_dir()),
        key=version_key,
    )
    if not versions:
        continue
    latest = versions[-1]
    stamp = time.strftime("%Y%m%d%H%M%S", time.gmtime())
    lines = ['<?xml version="1.0" encoding="UTF-8"?>', "<metadata>"]
    lines.append(f"  <groupId>{group_id}</groupId>")
    lines.append(f"  <artifactId>{artifact_dir.name}</artifactId>")
    lines.append("  <versioning>")
    lines.append(f"    <latest>{latest}</latest>")
    lines.append(f"    <release>{latest}</release>")
    lines.append("    <versions>")
    for v in versions:
        lines.append(f"      <version>{v}</version>")
    lines.append("    </versions>")
    lines.append(f"    <lastUpdated>{stamp}</lastUpdated>")
    lines.append("  </versioning>")
    lines.append("</metadata>")
    body = "\n".join(lines) + "\n"
    meta = artifact_dir / "maven-metadata.xml"
    meta.write_text(body)
    data = body.encode()
    for algo in ("md5", "sha1", "sha256", "sha512"):
        digest = hashlib.new(algo, data).hexdigest()
        (artifact_dir / f"maven-metadata.xml.{algo}").write_text(digest)
PY

echo "publish-maven: wrote $VERSION into $HOST/$GROUP_PATH (+ regenerated maven-metadata.xml)" >&2

if [[ "$COMMIT" == "--commit" ]]; then
  git -C "$HOST" add -A "$GROUP_PATH"
  git -C "$HOST" commit -m "kotlin-ksp-mocks $VERSION"
  echo "publish-maven: committed (push is the caller's step)" >&2
fi
