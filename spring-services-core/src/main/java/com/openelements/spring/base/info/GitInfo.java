package com.openelements.spring.base.info;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Git metadata for the running build.
 *
 * <p>A {@code GitInfo} exists only when a commit hash is known — the absence of a commit is
 * represented by a {@code null} {@link ApplicationInfo#git()}, not by a {@code GitInfo} full of
 * {@code null}s. The hash is sourced either from {@code META-INF/git.properties} (where {@code .git}
 * was present at build time) or, as a fallback, from {@code build.commit} in
 * {@code build-info.properties} (the container build that receives the hash as a build argument);
 * see {@link ApplicationInfoService} for the precedence rule.
 *
 * @param commitId the full commit hash; never {@code null}
 * @param shortCommitId the abbreviated commit hash — {@code git.commit.id.abbrev} when supplied,
 *     otherwise the first seven characters of {@code commitId} (or the whole hash when it is
 *     shorter than seven characters); never {@code null}
 * @param branch the branch name, or {@code null} if unknown (e.g. the container-build fallback)
 * @param tag the tag pointing at this commit, or {@code null} if the commit carries no tag
 * @param dirty {@link Boolean#TRUE} if the worktree had uncommitted changes, {@link Boolean#FALSE}
 *     if it was clean, or {@code null} if the source could not tell — a primitive {@code false}
 *     would conflate "clean" with "unknown", and the container build is precisely the case that
 *     cannot know
 * @param commitTime the commit timestamp, or {@code null} if unknown; a property of the commit, not
 *     of the build machine, so it is safe for reproducible builds
 */
public record GitInfo(
    String commitId,
    String shortCommitId,
    @Nullable String branch,
    @Nullable String tag,
    @Nullable Boolean dirty,
    @Nullable Instant commitTime) {

  /**
   * Validates that the two identifying fields are present.
   *
   * @param commitId the full commit hash; must not be {@code null}
   * @param shortCommitId the abbreviated commit hash; must not be {@code null}
   * @param branch the branch name, or {@code null}
   * @param tag the tag, or {@code null}
   * @param dirty the dirty flag, or {@code null} if unknown
   * @param commitTime the commit timestamp, or {@code null}
   */
  public GitInfo {
    Objects.requireNonNull(commitId, "commitId must not be null");
    Objects.requireNonNull(shortCommitId, "shortCommitId must not be null");
  }
}
