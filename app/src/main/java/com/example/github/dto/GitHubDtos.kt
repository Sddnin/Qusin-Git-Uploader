package com.example.github.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitHubUserDto(
  val login: String,
  val name: String? = null,
  @Json(name = "avatar_url") val avatarUrl: String? = null,
  @Json(name = "html_url") val htmlUrl: String? = null,
  @Json(name = "public_repos") val publicRepos: Int = 0,
  @Json(name = "total_private_repos") val totalPrivateRepos: Int? = null,
  @Json(name = "owned_private_repos") val ownedPrivateRepos: Int? = null,
)

@JsonClass(generateAdapter = true)
data class RepoPermissionsDto(
  val admin: Boolean = false,
  val maintain: Boolean = false,
  val push: Boolean = false,
  val triage: Boolean = false,
  val pull: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class RepoOwnerDto(
  val login: String,
)

@JsonClass(generateAdapter = true)
data class GitHubRepoDto(
  val id: Long,
  val name: String,
  @Json(name = "full_name") val fullName: String,
  val owner: RepoOwnerDto,
  val description: String? = null,
  @Json(name = "private") val isPrivate: Boolean = false,
  @Json(name = "default_branch") val defaultBranch: String = "main",
  @Json(name = "updated_at") val updatedAt: String = "",
  @Json(name = "html_url") val htmlUrl: String = "",
  @Json(name = "clone_url") val cloneUrl: String = "",
  val permissions: RepoPermissionsDto? = null,
  val size: Long = 0,
)

@JsonClass(generateAdapter = true)
data class RepoSearchResponseDto(
  @Json(name = "total_count") val totalCount: Int = 0,
  val items: List<GitHubRepoDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class CreateRepoRequestDto(
  val name: String,
  val description: String? = null,
  @Json(name = "private") val isPrivate: Boolean = false,
  @Json(name = "auto_init") val autoInit: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class BranchCommitRefDto(
  val sha: String,
)

@JsonClass(generateAdapter = true)
data class GitHubBranchDto(
  val name: String,
  val commit: BranchCommitRefDto,
  @Json(name = "protected") val isProtected: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class GitHubCommitItemDto(
  val sha: String,
  val commit: CommitDetailDto,
  @Json(name = "html_url") val htmlUrl: String = "",
)

@JsonClass(generateAdapter = true)
data class CommitDetailDto(
  val message: String,
  val author: CommitAuthorDto? = null,
)

@JsonClass(generateAdapter = true)
data class CommitAuthorDto(
  val name: String = "Developer",
  val date: String = "",
)

@JsonClass(generateAdapter = true)
data class GitRefObjectDto(
  val sha: String,
  val type: String = "commit",
)

@JsonClass(generateAdapter = true)
data class GitRefDto(
  val ref: String,
  @Json(name = "object") val obj: GitRefObjectDto,
)

@JsonClass(generateAdapter = true)
data class CreateGitRefRequestDto(
  val ref: String,
  val sha: String,
)

@JsonClass(generateAdapter = true)
data class UpdateGitRefRequestDto(
  val sha: String,
  val force: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class CreateBlobRequestDto(
  val content: String,
  val encoding: String = "base64",
)

@JsonClass(generateAdapter = true)
data class CreateBlobResponseDto(
  val sha: String,
)

@JsonClass(generateAdapter = true)
data class GitTreeEntryRequestDto(
  val path: String,
  val mode: String = "100644",
  val type: String = "blob",
  val sha: String?,
)

@JsonClass(generateAdapter = true)
data class CreateTreeRequestDto(
  @Json(name = "base_tree") val baseTree: String? = null,
  val tree: List<GitTreeEntryRequestDto>,
)

@JsonClass(generateAdapter = true)
data class CreateTreeResponseDto(
  val sha: String,
)

@JsonClass(generateAdapter = true)
data class GitTreeNodeDto(
  val path: String,
  val mode: String = "100644",
  val type: String = "blob",
  val sha: String,
  val size: Long? = null,
)

@JsonClass(generateAdapter = true)
data class GitTreeResponseDto(
  val sha: String,
  val truncated: Boolean = false,
  val tree: List<GitTreeNodeDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class GitBlobResponseDto(
  val sha: String,
  val size: Long = 0,
  val content: String = "",
  val encoding: String = "base64",
)

@JsonClass(generateAdapter = true)
data class CreateCommitRequestDto(
  val message: String,
  val tree: String,
  val parents: List<String>,
)

@JsonClass(generateAdapter = true)
data class CreateCommitResponseDto(
  val sha: String,
  @Json(name = "html_url") val htmlUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class GitCommitObjectDto(
  val sha: String,
  val tree: GitTreeRefDto,
)

@JsonClass(generateAdapter = true)
data class GitTreeRefDto(
  val sha: String,
)

@JsonClass(generateAdapter = true)
data class ContentFileDto(
  val name: String,
  val path: String,
  val sha: String,
  val content: String? = null,
  val encoding: String? = null,
)

@JsonClass(generateAdapter = true)
data class PutContentRequestDto(
  val message: String,
  val content: String,
  val branch: String? = null,
  val sha: String? = null,
)

@JsonClass(generateAdapter = true)
data class PutContentResponseDto(
  val commit: CreateCommitResponseDto? = null,
)

@JsonClass(generateAdapter = true)
data class GitHubReleaseAssetDto(
  val id: Long = 0,
  val name: String = "",
  @Json(name = "content_type") val contentType: String = "",
  val size: Long = 0,
  @Json(name = "browser_download_url") val browserDownloadUrl: String = "",
)

@JsonClass(generateAdapter = true)
data class GitHubReleaseDto(
  @Json(name = "tag_name") val tagName: String = "",
  val name: String? = null,
  val body: String? = null,
  val prerelease: Boolean = false,
  val draft: Boolean = false,
  @Json(name = "published_at") val publishedAt: String? = null,
  @Json(name = "html_url") val htmlUrl: String = "",
  val assets: List<GitHubReleaseAssetDto> = emptyList(),
)
