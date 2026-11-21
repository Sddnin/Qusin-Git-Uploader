package com.example.github.api

import com.example.github.dto.ContentFileDto
import com.example.github.dto.CreateBlobRequestDto
import com.example.github.dto.CreateBlobResponseDto
import com.example.github.dto.CreateCommitRequestDto
import com.example.github.dto.CreateCommitResponseDto
import com.example.github.dto.CreateGitRefRequestDto
import com.example.github.dto.CreateRepoRequestDto
import com.example.github.dto.CreateTreeRequestDto
import com.example.github.dto.CreateTreeResponseDto
import com.example.github.dto.GitBlobResponseDto
import com.example.github.dto.GitCommitObjectDto
import com.example.github.dto.GitHubBranchDto
import com.example.github.dto.GitHubCommitItemDto
import com.example.github.dto.GitHubReleaseDto
import com.example.github.dto.GitHubRepoDto
import com.example.github.dto.GitHubUserDto
import com.example.github.dto.GitRefDto
import com.example.github.dto.GitTreeResponseDto
import com.example.github.dto.PutContentRequestDto
import com.example.github.dto.PutContentResponseDto
import com.example.github.dto.RepoSearchResponseDto
import com.example.github.dto.UpdateGitRefRequestDto
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface GitHubApiService {
  @Headers("Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
  @GET("user")
  suspend fun getAuthenticatedUser(): Response<GitHubUserDto>

  @Headers("Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
  @GET("user")
  suspend fun validateTokenWithExplicitHeader(
    @Header("Authorization") authorization: String,
  ): Response<GitHubUserDto>

  @Headers("Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
  @GET("user/repos")
  suspend fun listUserRepos(
    @Query("sort") sort: String = "updated",
    @Query("per_page") perPage: Int = 50,
    @Query("affiliation") affiliation: String = "owner,collaborator,organization_member",
  ): Response<List<GitHubRepoDto>>

  @Headers("Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
  @GET("search/repositories")
  suspend fun searchRepos(
    @Query("q") query: String,
    @Query("per_page") perPage: Int = 30,
  ): Response<RepoSearchResponseDto>

  @Headers("Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
  @POST("user/repos")
  suspend fun createRepo(@Body body: CreateRepoRequestDto): Response<GitHubRepoDto>

  @Headers("Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
  @GET("repos/{owner}/{repo}")
  suspend fun getRepo(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
  ): Response<GitHubRepoDto>

  @Headers("Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
  @GET("repos/{owner}/{repo}/branches")
  suspend fun listBranches(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Query("per_page") perPage: Int = 100,
  ): Response<List<GitHubBranchDto>>

  @Headers("Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
  @GET("repos/{owner}/{repo}/commits")
  suspend fun listCommits(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Query("sha") sha: String,
    @Query("per_page") perPage: Int = 25,
  ): Response<List<GitHubCommitItemDto>>

  @Headers("Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
  @GET("repos/{owner}/{repo}/git/ref/heads/{branch}")
  suspend fun getBranchRef(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path("branch", encoded = true) branch: String,
  ): Response<GitRefDto>

  @Headers("Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
  @POST("repos/{owner}/{repo}/git/refs")
  suspend fun createRef(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Body body: CreateGitRefRequestDto,
  ): Response<GitRefDto>

  @Headers("Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
  @PATCH("repos/{owner}/{repo}/git/refs/heads/{branch}")
  suspend fun updateBranchRef(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path("branch", encoded = true) branch: String,
    @Body body: UpdateGitRefRequestDto,
  ): Response<GitRefDto>

  @Headers("Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
  @GET("repos/{owner}/{repo}/git/commits/{sha}")
  suspend fun getGitCommit(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path("sha") sha: String,
  ): Response<GitCommitObjectDto>

  @Headers("Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
  @POST("repos/{owner}/{repo}/git/blobs")
  suspend fun createBlob(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Body body: CreateBlobRequestDto,
  ): Response<CreateBlobResponseDto>

  @Headers("Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
  @GET("repos/{owner}/{repo}/git/trees/{tree_sha}")
  suspend fun getGitTree(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path("tree_sha") treeSha: String,
    @Query("recursive") recursive: Int = 1,
  ): Response<GitTreeResponseDto>

  @Headers("Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
  @GET("repos/{owner}/{repo}/git/blobs/{sha}")
  suspend fun getGitBlob(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path("sha") sha: String,
  ): Response<GitBlobResponseDto>

  @Headers("Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
  @POST("repos/{owner}/{repo}/git/trees")
  suspend fun createTree(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Body body: CreateTreeRequestDto,
  ): Response<CreateTreeResponseDto>

  @Headers("Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
  @POST("repos/{owner}/{repo}/git/trees")
  suspend fun createTreeRaw(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Body body: RequestBody,
  ): Response<CreateTreeResponseDto>

  @Headers("Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
  @POST("repos/{owner}/{repo}/git/commits")
  suspend fun createCommit(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Body body: CreateCommitRequestDto,
  ): Response<CreateCommitResponseDto>

  @Headers("Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
  @GET("repos/{owner}/{repo}/contents/{path}")
  suspend fun getContentFile(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path("path", encoded = true) path: String,
    @Query("ref") ref: String? = null,
  ): Response<ContentFileDto>

  @Headers("Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
  @PUT("repos/{owner}/{repo}/contents/{path}")
  suspend fun putContentFile(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path("path", encoded = true) path: String,
    @Body body: PutContentRequestDto,
  ): Response<PutContentResponseDto>

  @Streaming
  @GET("repos/{owner}/{repo}/zipball/{ref}")
  suspend fun downloadZipball(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path("ref", encoded = true) ref: String,
  ): Response<ResponseBody>

  @Headers("Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
  @GET("repos/{owner}/{repo}/releases/latest")
  suspend fun getLatestRelease(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
  ): Response<GitHubReleaseDto>

  @Headers("Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
  @GET("repos/{owner}/{repo}/releases")
  suspend fun listReleases(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Query("per_page") perPage: Int = 5,
  ): Response<List<GitHubReleaseDto>>
}
