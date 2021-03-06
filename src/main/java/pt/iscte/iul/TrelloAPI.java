package pt.iscte.iul;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

import static java.lang.Integer.parseInt;

/**
 * @author Duarte Casaleiro, Oleksandr Kobelyuk, Miguel Romana.
 */
public class TrelloAPI {
    private final String apiKey;
    private final String apiToken;
    private final String boardName;

    private final String boardURL = "https://api.trello.com/1/boards/";
    private final String cardURL = "https://api.trello.com/1/cards/";
    private final String listURL = "https://api.trello.com/1/lists/";

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;

    private final String boardId;

    /**
     * Base class for requesting information from the Trello API.
     *
     * @param boardName Name of the board.
     * @param apiKey    Trello API access key.
     * @param apiToken  Trello API access token.
     */
    public TrelloAPI(String boardName, String apiKey, String apiToken) throws IOException {
        this.apiKey = apiKey;
        this.apiToken = apiToken;
        this.boardName = boardName;

        this.mapper = new ObjectMapper();
        this.httpClient = new OkHttpClient();

        this.mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.mapper.setVisibility(VisibilityChecker.Std.defaultInstance().withFieldVisibility(JsonAutoDetect.Visibility.ANY));

        this.boardId = this.getBoardID();
    }

    /**
     * Board object.
     */
    public static class Board {
        private String name;
        private String id;
        private String url;

        /**
         * @return The name.
         */
        public String getName() {
            return this.name;
        }

        /**
         * @return The ID.
         */
        public String getId() {
            return this.id;
        }

        /**
         * @return The url.
         */
        public String getUrl() {
            return this.url;
        }
    }

    /**
     * @return a list of all boards owned by the user.
     * @throws IOException If the request fails.
     */
    public Board[] getBoards() throws IOException {
        var request = new Request.Builder()
                .header("Accept", "application/json")
                .url("https://api.trello.com/1/members/me/boards?key=" + this.apiKey + "&token=" + this.apiToken).build();

        var response = this.httpClient.newCall(request).execute();

        return mapper.readValue(response.body().string(), Board[].class);
    }

    private String getBoardID() throws IOException {
        var boards = this.getBoards();

        for (var board : boards) {
            if (Objects.equals(board.getName(), this.boardName)) {
                return board.getId();
            }
        }

        return "not found";
    }

    /**
     * @param component   Component that we want to access (list, card, board, etc).
     * @param componentId ID of the component that we want to access.
     * @param url         Url of the component (board url, list url, etc).
     * @return A {@link Response} object.
     * @throws IOException If the request fails.
     */
    private Response httpRequest(String component, String componentId, String url) throws IOException {
        //HTTP request to access
        var request = new Request.Builder()
                .header("Accept", "application/json")
                .url(url + componentId + "/" + component + "?key=" + apiKey + "&token=" + apiToken).build();

        return this.httpClient.newCall(request).execute();
    }

    /**
     * @return Board information associated to the boardId obtained from {@link TrelloAPI#getBoardID()}.
     * @throws IOException If the request fails.
     */
    public Board getBoardInfo() throws IOException {
        //HTTP request to access the board
        var response = this.httpRequest("", this.boardId, this.boardURL);

        // map http response to the class Board
        return mapper.readValue(response.body().string(), Board.class);
    }

    /**
     * List object.
     */
    public static class List {
        private String name;
        private String id;

        /**
         * @return The name.
         */
        public String getName() {
            return this.name;
        }

        /**
         * @return The ID.
         */
        public String getId() {
            return this.id;
        }
    }

    /**
     * @return All lists in the board.
     * @throws IOException If the request fails.
     */
    public List[] getBoardLists() throws IOException {
        //HTTP request to access the lists
        var response = this.httpRequest("lists", this.boardId, this.boardURL);

        // map http response to the class List
        return mapper.readValue(response.body().string(), List[].class);
    }

    /**
     * @param listName List name.
     * @return The list in the board identified by the board id.
     */
    public List getList(String listName) throws IOException {
        for (var list : this.getBoardLists()) {
            if (list.getName().equals(listName)) {
                return list;
            }
        }
        return null;
    }

    /**
     * @return All cards in the board identified by the board ID.
     * @throws IOException If the request fails.
     */
    public Card[] getBoardCards() throws IOException {
        //HTTP request to access all cards in the board
        var response = this.httpRequest("cards", this.boardId, this.boardURL);

        // map http response to the class Card
        return mapper.readValue(response.body().string(), Card[].class);
    }

    /**
     * Card object.
     */
    public static class Card {
        private String name;
        private String id;
        private String due;
        private String created;
        private String desc;
        private Member member;

        /**
         * @return The name.
         */
        public String getName() {
            return this.name;
        }

        /**
         * @return The ID.
         */
        public String getId() {
            return this.id;
        }

        /**
         * @return The due date.
         */
        public String getDueDate() {
            return this.due == null ? "N/A" :
                    this.due.split("T")[0]; // split by delimiter T
        }

        /**
         * @return The date it was created.
         */
        public String getCreatedDate() {
            this.created = new SimpleDateFormat("yyyy-MM-dd").
                    format(new Date(1000L * parseInt(this.getId().substring(0, 8), 16)));
            return this.created;
        }

        /**
         * @return The description.
         */
        public String getDescription() {
            return this.desc;
        }
    }

    /**
     * @param listId List ID.
     * @return All cards in the list identified by the list id.
     * @throws IOException If the request fails.
     */
    public Card[] getListCards(String listId) throws IOException {
        //HTTP request to access a List
        var response = this.httpRequest("cards", listId, this.listURL);

        // map http response to the class List
        return mapper.readValue(response.body().string(), Card[].class);
    }

    /**
     * Member object.
     */
    public static class Member {
        private String username;
        private String id;

        /**
         * @return The name.
         */
        public String getName() {
            return this.username;
        }

        /**
         * @return The ID.
         */
        public String getId() {
            return this.id;
        }
    }

    /**
     * @param cardId Card ID.
     * @return All members in the card identified by the card ID.
     * @throws IOException If the request fails.
     */
    public Member[] getMemberOfCard(String cardId) throws IOException {
        //HTTP request to access all Members of a Card
        var response = this.httpRequest("members", cardId, this.cardURL);
        // map http response to the class Member
        return mapper.readValue(response.body().string(), Member[].class);
    }

    /**
     * @param sprintNumber Sprint number.
     * @return An array with the start date [0] and the end date [1] of the specific sprint.
     * @throws IOException If the request fails.
     */
    public String[] getSprintDates(int sprintNumber) throws IOException {
        // flag to see if we've found the start date
        boolean startDateFound = false;
        // initialize list of dates
        var dates = new String[2];
        // get the list of all ceremonies
        var list = this.getList("Ceremonies - Sprint " + sprintNumber);
        // Iterate over all cards in the list
        for (var c : this.getListCards(list.getId())) {
            // search for due date of Sprint Planning that is equal to Sprint start date
            if (c.name.equals("Sprint Planning - Sprint " + sprintNumber)) {
                dates[0] = c.getDueDate();
                startDateFound = true;
            }
            // search for due date of Sprint Retrospective that is equal to Sprint end date
            else if (c.name.equals("Sprint Retrospective - Sprint " + sprintNumber)) {
                dates[1] = c.getDueDate();
                if (startDateFound) break; // if start date is found, we can break the for loop
            }
        }
        return dates;
    }

    /**
     * @param sprintType   Sprint type.
     * @param sprintNumber Sprint number.
     * @return Description of the ceremony in question.
     * @throws IOException If the request fails.
     * @author Miguel Romana.
     */
    public String getCeremonyDescription(String sprintType, int sprintNumber) throws IOException {
        var list = this.getList("Ceremonies - Sprint " + sprintNumber);
        for (var c : this.getListCards(list.getId())) {
            if (c.name.equals("Sprint " + sprintType + " - Sprint " + sprintNumber)) {
                return c.getDescription();
            }
        }
        return ""; // returns an empty String if the description doesn't exist
    }

    /**
     * @param sprintNumber Sprint number.
     * @return An {@link ArrayList} of all the products already done in the specific sprint.
     * @throws IOException If the request fails.
     */
    public ArrayList<String> getDoneProductBacklog(int sprintNumber) throws IOException {
        var doneItems = new ArrayList<String>();
        // get specific list
        var list = this.getList("Done - Sprint " + sprintNumber);
        for (var card : this.getListCards(list.getId())) {
            doneItems.add(card.name);
        }
        return doneItems;
    }

    /**
     * @return Number of ceremonies done by the team.
     * @throws IOException If the request fails.
     */
    public int getTotalNumberOfCeremonies() throws IOException {
        var numberOfCeremonies = 0;
        var ceremoniesLists = this.queryLists("Ceremonies");
        for (var ceremoniesList : ceremoniesLists) {
            var ceremoniesListCards = this.getListCards(ceremoniesList.id);
            numberOfCeremonies += ceremoniesListCards.length;
        }
        return numberOfCeremonies;
    }

    /**
     * @param sprintNumber Sprint number.
     * @return Number of ceremonies.
     * @throws IOException If the request fails.
     */
    public int getTotalNumberOfCeremoniesPerSprint(int sprintNumber) throws IOException {
        var lists = this.getBoardLists();
        for (var ceremoniesList : lists) {
            if (ceremoniesList.getName().equals("Ceremonies - Sprint " + sprintNumber)) {
                var ceremoniesListCards = this.getListCards(ceremoniesList.id);
                return ceremoniesListCards.length;
            }
        }
        return 0;
    }

    /**
     * Method that returns the dates of features' and tests' implementation.
     *
     * @return A {@link Map} with the start and end dates of features and tests and the card associated.
     * @throws IOException If the request fails.
     * @author Miguel Romana.
     */
    public Map<Card, String[]> getFeaturesAndTestsDates() throws IOException {
        var cardDates = new HashMap<Card, String[]>();
        var lists = this.queryLists("Done");
        for (var list : lists) {
            var cards = getListCards(list.id);
            for (var card : cards) {
                cardDates.put(card, new String[]{card.getCreatedDate(), card.getDueDate()});
            }
        }
        return cardDates;
    }

    /**
     * @param query query for the list name.
     * @return An {@link ArrayList} of {@link List} that match the query.
     * @throws IOException If the request fails.
     */
    public ArrayList<List> queryLists(String query, boolean... exclude) throws IOException {
        var allLists = this.getBoardLists();
        var listsThatStartWith = new ArrayList<List>();
        for (var list : allLists) {
            if (exclude.length == 1 ? (exclude[0] != list.getName().contains(query)) : list.getName().contains(query)) {
                listsThatStartWith.add(list);
            }
        }
        return listsThatStartWith;
    }

    /**
     * @return Hours spent by the team on ceremonies.
     * @throws IOException If the request fails.
     */
    public double getTotalCeremonyHours() throws IOException {
        var pattern = Pattern.compile("(?:@global (\\d*.?\\d+)/(\\d*.?\\d+))");
        var totalOfHours = 0.0;
        var listOfCeremonies = this.queryLists("Ceremonies");
        for (var list : listOfCeremonies) {
            for (var card : this.getListCards(list.getId())) {
                var match = pattern.matcher(card.getDescription());
                while (match.find()) {
                    totalOfHours += Double.parseDouble(match.group(1));
                }
            }
        }
        return totalOfHours;
    }

    /**
     * Contains relevant information about the time a user spent on the project.
     */
    static class HoursPerUser {
        private String user;
        private double spentHours;
        private double estimatedHours;
        private int cards = 0;

        /**
         * Class to get all hours spent and estimated by user.
         *
         * @param user           Member.
         * @param spentHours     spent hours.
         * @param estimatedHours estimated hours.
         */
        public HoursPerUser(String user, double spentHours, double estimatedHours) {
            this.user = user;
            this.spentHours = spentHours;
            this.estimatedHours = estimatedHours;
        }

        /**
         * @return The user.
         */
        public String getUser() {
            return user;
        }

        /**
         * @return The hours that were spent.
         */
        public double getSpentHours() {
            return spentHours;
        }

        /**
         * @return The hours that were estimated.
         */
        public double getEstimatedHours() {
            return estimatedHours;
        }

        private void addSpentHours(double hours) {
            this.spentHours += hours;
        }

        private void addEstimatedHours(double hours) {
            this.estimatedHours += hours;
        }

        private void addCard() {
            this.cards++;
        }

        /**
         * @return The number of cards associated to the user.
         */
        public int getCards() {
            return cards;
        }

        @Override
        // auto generated
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            HoursPerUser that = (HoursPerUser) o;
            return Objects.equals(user, that.user);
        }
    }

    private void addObjectToList(Card card, Pattern pattern, ArrayList<HoursPerUser> hoursPerUser) throws IOException {
        for (var member : this.getMemberOfCard(card.getId())) {
            if (!hoursPerUser.contains(new HoursPerUser(member.getName(), 0.0, 0.0))) {
                hoursPerUser.add(new HoursPerUser(member.getName(), 0.0, 0.0));
            }
        }
        var match = pattern.matcher(card.getDescription());
        while (match.find()) {
            for (var o : hoursPerUser) {
                if (Objects.equals(o.getUser(), match.group(1))) {
                    o.addSpentHours(Double.parseDouble(match.group(2)));
                    o.addEstimatedHours(Double.parseDouble(match.group(3)));
                    o.addCard();
                }
            }
        }
    }

    /**
     * @param listQuery query for the list name.
     * @param cardQuery query for the card name.
     * @return the total hours spent by user in a list of {@link HoursPerUser}
     * @throws IOException If the request fails.
     */
    public ArrayList<HoursPerUser> getTotalHoursByUser(String listQuery, String cardQuery, boolean... exclude) throws IOException {
        var pattern = Pattern.compile("(?:@(.+) (\\d*.?\\d+)/(\\d*.?\\d+))");
        var hoursPerUser = new ArrayList<HoursPerUser>();
        var listOfCeremonies = this.queryLists(listQuery, exclude);
        for (var list : listOfCeremonies) {
            for (var card : this.getListCards(list.getId())) {
                if (!card.getName().contains(cardQuery))
                    continue;
                addObjectToList(card, pattern, hoursPerUser);
            }
        }
        return hoursPerUser;
    }

    /**
     * Converts relevant information into CSV strings.
     *
     * @param rate            Hourly rate.
     * @param numberOfSprints Number of Sprints.
     * @return An array of CSV formatted strings.
     * @throws IOException If the request fails.
     */
    public String convertToCSV(int rate, int numberOfSprints) throws IOException {
        // 8 & 9
        var csv8 = new ArrayList<String>();
        csv8.add("Sprint,Elemento,Horas Usadas,Horas Previstas\n");

        var csv9 = new ArrayList<String>();
        csv9.add("Sprint,Elemento,Salario\n");

        for (var i = 1; i < numberOfSprints + 1; i++) {
            var hoursPerUser = this.getTotalHoursByUser("", "Sprint " + i);

            for (var user : hoursPerUser) {
                csv9.add(
                        i + "," + user.user + "," + user.spentHours * rate + "\n"
                );

                csv8.add(
                        i + "," + user.getUser() + "," + user.getSpentHours() + "," + user.getEstimatedHours() + "\n"
                );
            }
        }

        var hoursPerUser = this.getTotalHoursByUser("", "");
        csv9.add(",Total, " + hoursPerUser.stream().map(user -> user.spentHours * rate).mapToDouble(d -> d).sum() + "\n");

        // 10
        var csv10 = new ArrayList<String>();
        csv10.add("Elemento,Atividades,Horas,Custo\n");

        var users10 = this.getTotalHoursByUser("Ceremonies", "", true);
        for (var perUser : users10) {
            csv10.add(
                    perUser.getUser()
                            + "," + perUser.getCards()
                            + "," + perUser.getSpentHours()
                            + "," + perUser.getSpentHours() * rate + "\n"
            );
        }

        var lists = this.queryLists("Ceremonies", true);
        var numberOfCards = lists.stream().map(list -> {
            try {
                return this.getListCards(list.getId()).length;
            } catch (IOException e) {
                e.printStackTrace();
            }

            return 0;
        }).mapToInt(i -> i).sum();

        var hours = users10.stream().map(HoursPerUser::getSpentHours).mapToDouble(d -> d).sum();

        csv10.add("Global," + numberOfCards + "," + hours + "," + hours * rate + "\n");

        // 11
        var csv11 = new ArrayList<String>();
        csv11.add("Elemento,Atividades,Horas,Custo\n");

        var users11 = this.getTotalHoursByUser("Ceremonies", "");
        for (var perUser : users11) {
            csv11.add(
                    perUser.getUser()
                            + "," + perUser.getCards()
                            + "," + perUser.getSpentHours()
                            + "," + perUser.getSpentHours() * rate + "\n"
            );
        }

        lists = this.queryLists("Ceremonies");
        numberOfCards = lists.stream().map(list -> {
            try {
                return this.getListCards(list.getId()).length;
            } catch (IOException e) {
                e.printStackTrace();
            }

            return 0;
        }).mapToInt(i -> i).sum();

        hours = users11.stream().map(HoursPerUser::getSpentHours).mapToDouble(d -> d).sum();

        csv11.add("Global," + numberOfCards + "," + hours + "," + hours * rate + "\n");

        return String.join("", csv8) + "\n"
                + String.join("", csv9) + "\n"
                + "Cartoes que geraram artefactos\n"
                + String.join("", csv10) + "\n"
                + "Cartoes que nao geraram artefactos\n"
                + String.join("", csv11);
    }

}