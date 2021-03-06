package pt.iscte.iul;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

/**
 * @author Oleksandr Kobelyuk.
 */
public class GitHubAPI {
    private final String apiKey;
    private final String baseAPIUrl;
    private final String baseRawUrl;
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;

    // simple cache
    private final Collaborators[] cachedCollaborators;
    private final Branch[] cachedBranches;

    /**
     * Base class for requesting information from the GitHub API.
     *
     * @param repoOwner   Owner of the repository.
     * @param projectName Name of the project.
     * @param apiKey      GitHub API access key.
     */
    public GitHubAPI(String repoOwner, String projectName, String apiKey) throws IOException {
        this.apiKey = apiKey;

        this.baseAPIUrl = "https://api.github.com/repos/" + repoOwner + "/" + projectName;
        this.baseRawUrl = "https://raw.githubusercontent.com/" + repoOwner + "/" + projectName;

        this.httpClient = new OkHttpClient();

        this.mapper = new ObjectMapper();
        this.mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.mapper.setVisibility(VisibilityChecker.Std.defaultInstance().withFieldVisibility(JsonAutoDetect.Visibility.ANY));

        this.cachedCollaborators = this.getCollaborators();
        this.cachedBranches = this.getBranches();
    }

    /**
     * Simple datetime object.
     */
    public static class Date {
        private final String formatted;
        private final String year;
        private final String month;
        private final String day;

        /**
         * @param raw The raw date string YYYY-MM-DD-Thh-mm-ssZ.
         */
        public Date(String raw) {
            this.formatted = raw.split("T")[0];
            var data = this.formatted.split("-");

            this.year = data[0];
            this.month = data[1];
            this.day = data[2];
        }

        /**
         * @return The year.
         */
        public String getYear() {
            return this.year;
        }

        /**
         * @return The month.
         */
        public String getMonth() {
            return this.month;
        }

        /**
         * @return The day.
         */
        public String getDay() {
            return this.day;
        }

        /**
         * @return A YYYY-MM-DD formatted string.
         */
        @Override
        public String toString() {
            return formatted;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Date date = (Date) o;
            return  Objects.equals(year, date.year) && Objects.equals(month, date.month) && Objects.equals(day, date.day);
        }
    }

    private static class Repo {
        private String created_at;

        public String getCreatedAt() {
            return this.created_at;
        }
    }

    /**
     * @return A {@link Date} object with the repository creation date.
     * @throws IOException If the request fails.
     */
    public Date getStartTime() throws IOException {
        var request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + apiKey)
                .url(this.baseAPIUrl).build();

        var resp = this.httpClient.newCall(request).execute();

        var mapped = this.mapper.readValue(Objects.requireNonNull(resp.body()).string(), Repo.class);
        return new Date(mapped.getCreatedAt());
    }

    private static class User {
        private String name;

        /**
         * @return The collaborator's name.
         */
        public String getName() {
            return this.name;
        }
    }

    /**
     * Stores relevant information about a collaborator.
     */
    public static class Collaborators {
        private String login;
        private String avatar_url;
        private String html_url;

        private String name;

        /**
         * @return The GitHub handle.
         */
        public String getLogin() {
            return this.login;
        }

        /**
         * @return The profile picture url.
         */
        public String getAvatar() {
            return this.avatar_url;
        }

        /**
         * @return The GitHub profile url.
         */
        public String getProfile() {
            return this.html_url;
        }

        /**
         * @return The name.
         */
        @Nullable
        public String getName() {
            return name;
        }

        private void setName(String name) {
            this.name = name;
        }
    }

    /**
     * @return An array of {@link Collaborators}.
     * @throws IOException If the request fails.
     */
    public Collaborators[] getCollaborators() throws IOException {
        if (this.cachedCollaborators != null && this.cachedCollaborators.length != 0) {
            return this.cachedCollaborators;
        }

        var request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + apiKey)
                .url(this.baseAPIUrl + "/collaborators").build();

        var resp = this.httpClient.newCall(request).execute();

        var mapped = this.mapper.readValue(
                Objects.requireNonNull(resp.body()).string(),
                Collaborators[].class
        );
        for (var collaborator : mapped) {
            resp = this.httpClient.newCall(
                    new Request.Builder().url("https://api.github.com/users/" + collaborator.login).build()
            ).execute();

            collaborator.setName(this.mapper.readValue(
                    Objects.requireNonNull(resp.body()).string(),
                    User.class
            ).getName());
        }

        return mapped;
    }

    /**
     * @param branch Branch name.
     * @param path   Path of file(from root) in the branch.
     * @return File contents if it exists, otherwise '404: Not Found'.
     * @throws IOException If the request fails.
     */
    public String getFile(String branch, String path) throws IOException {
        var request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + apiKey)
                .url(this.baseRawUrl + "/" + branch + path).build();

        var resp = this.httpClient.newCall(request).execute();

        return Objects.requireNonNull(resp.body()).string();
    }

    /**
     * Stores the branch name.
     * The JSON response is unloaded directly into this object.
     */
    public static class Branch {
        private String name;

        /**
         * @return The name of the branch.
         */
        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Branch && Objects.equals(this.name, ((Branch) o).name);
        }
    }

    /**
     * Function that retrieves all the repository branches.
     *
     * @return An array of {@link Branch} objects.
     * @throws IOException If the request fails.
     */
    public Branch[] getBranches() throws IOException {
        if (this.cachedBranches != null && this.cachedBranches.length != 0) {
            return this.cachedBranches;
        }

        var request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + apiKey)
                .url(this.baseAPIUrl + "/branches").build();

        var resp = this.httpClient.newCall(request).execute();
        return this.mapper.readValue(Objects.requireNonNull(resp.body()).string(), Branch[].class);
    }

    /**
     * Stores important data about a commit.
     */
    public record CommitData(Date date, String message) {

    }

    /**
     * Stores a user and their commits ordered from oldest to newest.
     */
    public static class Commits {
        private final String user;
        private final List<CommitData> commitData = new ArrayList<>();

        private Commits(String user, List<Commit> commits) {
            this.user = user;

            for (var commit : commits) {
                this.commitData.add(
                        new CommitData(
                                commit.getCommitDate(),
                                commit.getCommitMessage()
                        )
                );
            }

            Collections.reverse(this.commitData);
        }

        /**
         * Can be empty, see "user" in {@link GitHubAPI#getCommits(String, String)}
         *
         * @return The committer's name.
         */
        public String getCommitter() {
            return this.user;
        }

        /**
         * @return A list of {@link CommitData}.
         */
        public List<CommitData> getCommitList() {
            return commitData;
        }
    }

    private static class Commit {
        private Date commitDate;
        private String commitMessage;

        @SuppressWarnings("unchecked")
        @JsonProperty("commit")
        private void unpackNested(Map<String, Object> commit) {
            this.commitMessage = (String) commit.get("message");
            Map<String, String> committer = (Map<String, String>) commit.get("committer");
            this.commitDate = new Date(committer.get("date"));
        }

        public Date getCommitDate() {
            return commitDate;
        }

        public String getCommitMessage() {
            return commitMessage;
        }
    }

    /**
     * Retrieves commits per branch per user.
     * <ul>
     *     <li>If "user" is empty, retrieves all the commits in the branch.</li>
     *     <li>If both parameters are empty, branchName defaults to the main branch.</li>
     *     <li>If anything fails, a {@link Commits} object with no commits is returned.</li>
     * </ul>
     *
     * @param branch Branch name.
     * @param user   The username of the user in question, can be empty.
     * @return A {@link Commits} object.
     * @throws IOException If the request fails.
     */
    public Commits getCommits(String branch, String user) throws IOException {
        var commitBuffer = new ArrayList<Commit>();

        var currentPage = 1;
        // this way we can easily set the page number
        var formattableUrl = this.baseAPIUrl + "/commits?" + (Objects.equals(user, "") ? "" : "&author=" + user) + "&sha=" + branch + "&page=%d";

        while (true) {
            var request = new Request.Builder()
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .url(String.format(formattableUrl, currentPage++)).build();

            var resp = this.httpClient.newCall(request).execute();

            try {
                var ret = this.mapper.readValue(
                        Objects.requireNonNull(resp.body()).string(),
                        Commit[].class
                );

                if (ret.length == 0) break;

                commitBuffer.addAll(Arrays.asList(ret));
            } catch (MismatchedInputException e) {
                break;
            }
        }

        return new Commits(user, commitBuffer);
    }

    private static class Tag {
        private String name;
        private String sha;

        @SuppressWarnings("unchecked")
        @JsonProperty("commit")
        private void unpackNested(Map<String, Object> commit) {
            this.sha = (String) commit.get("sha");
        }

        public String getName() {
            return name;
        }

        public String getSha() {
            return sha;
        }
    }

    /**
     * Contains relevant information about a Tag.
     */
    public record TagData(String name, Date date) {
        @Override
        public String toString() {
            return "TagData{" +
                    "name='" + name + '\'' +
                    ", date=" + date +
                    '}';
        }
    }

    /**
     * Retrieves the tags of the master branch.
     * @return A list of {@link TagData}
     * @throws IOException If a request fails.
     */
    public List<TagData> getTags() throws IOException {
        var tagData = new ArrayList<TagData>();

        var request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + apiKey)
                .url(this.baseAPIUrl + "/tags").build();

        var resp = this.httpClient.newCall(request).execute();
        var mapped = this.mapper.readValue(Objects.requireNonNull(resp.body()).string(), Tag[].class);
        for (var tag : mapped) {
            request = new Request.Builder()
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .url(this.baseAPIUrl + "/commits/" + tag.getSha()).build();

            resp = this.httpClient.newCall(request).execute();
            var commit = this.mapper.readValue(Objects.requireNonNull(resp.body()).string(), Commit.class);

            tagData.add(
                    new TagData(
                            tag.getName(),
                            commit.getCommitDate()
                    )
            );
        }

        return tagData;
    }

    /**
     * Exports {@link Collaborators}, {@link Branch} and {@link CommitData} to a CSV and HTML formatted strings.
     * @return CSV and HTML formatted strings.
     * @throws IOException If a request fails.
     */
    public String[] convert() throws IOException {
        var csv = new ArrayList<String>();
        var html = new ArrayList<String>();

        csv.add("Contributor,Branch,Mensagem do Commit,Data do Commit (MM/DD/YYYY)\n");

        html.add("""
                <tr>
                    <th>Contributor</th>
                    <th>Branch</th>
                    <th>Mensagem do Commit</th>
                    <th>Data do Commit (YYYY/MM/DD)</th>
                </tr>
                """);

        var previousUser = "";
        var previousBranch = "";
        for (var user : this.getCollaborators()) {
            for (var branch : this.getBranches()) {
                var commitsForUser = this.getCommits(branch.getName(), user.getLogin());
                for (var commit : commitsForUser.getCommitList()) {
                    csv.add(
                            String.format(
                                    "%s,%s,%s,%s\n",
                                    Objects.equals(previousUser, user.getLogin()) ? "" : user.getLogin(),
                                    Objects.equals(previousBranch, branch.getName()) ? "" : branch.getName(),
                                    commit.message().replace(',', ' ').split("\n")[0],
                                    commit.date()
                            )
                    );

                    html.add(
                            String.format("""
                                    <tr>
                                        <td>%s</th>
                                        <td>%s</th>
                                        <td>%s</th>
                                        <td>%s</th>
                                    </tr>
                                    """,
                                    Objects.equals(previousUser, user.getLogin()) ? "" : user.getLogin(),
                                    Objects.equals(previousBranch, branch.getName()) ? "" : branch.getName(),
                                    commit.message().replace("\n", "<br>"),
                                    commit.date()
                            )
                    );

                    previousUser = user.getLogin();
                    previousBranch = branch.getName();
                }
            }
        }

        return new String[]{
                String.join("", csv),
                "<table border=1>\n" + String.join("", html) + "</table>"
        };
    }
}
