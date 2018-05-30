import java.io.*;
import java.net.*;
import java.util.Date;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.stage.Stage;

public class Connect4Client extends Application
        implements Connect4Constants {
    // Indicate whether the player has the turn
    private boolean myTurn = false;

    // Indicate the token for the player
    private String myToken = " ";

    // Indicate the token for the other player
    private String otherToken = " ";

    // Create and initialize cells
    private Cell[][] cell =  new Cell[6][7];

    // Create and initialize a title label
    private Label lblTitle = new Label();

    // Create and initialize a status label
    private Label lblStatus = new Label();

    // Indicate selected row and column by the current move
    private int rowSelected;
    private int columnSelected;

    // Input and output streams from/to server
    private DataInputStream fromServer;
    private DataOutputStream toServer;

    // Continue to play?
    private boolean continueToPlay = true;

    // Wait for the player to mark a cell
    private boolean waiting = true;

    // Host name or ip
    private String host = "localhost";

    @Override // Override the start method in the Application class
    public void start(Stage primaryStage) {
        // Pane to hold cell
        GridPane pane = new GridPane();
        for (int i = 0; i < 6; i++)
            for (int j = 0; j < 7; j++)
                pane.add(cell[i][j] = new Cell(i, j), j, i);

        BorderPane borderPane = new BorderPane();
        borderPane.setTop(lblTitle);
        borderPane.setCenter(pane);
        borderPane.setBottom(lblStatus);

        // Create a scene and place it in the stage
        Scene scene = new Scene(borderPane, 320, 350);
        primaryStage.setTitle("TicTacToeClient"); // Set the stage title
        primaryStage.setScene(scene); // Place the scene in the stage
        primaryStage.show(); // Display the stage

        // Connect to the server
        connectToServer();
    }

    private void connectToServer() {
        try {
            // Create a socket to connect to the server
            Socket socket = new Socket(host, 8000);

            // Create an input stream to receive data from the server
            fromServer = new DataInputStream(socket.getInputStream());

            // Create an output stream to send data to the server
            toServer = new DataOutputStream(socket.getOutputStream());
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

        // Control the game on a separate thread
        new Thread(() -> {
            try {
                // Get notification from the server
                int player = fromServer.readInt();

                // Am I player 1 or 2?
                if (player == PLAYER1) {
                    myToken =  "GREEN";
                    otherToken = "RED";
                    Platform.runLater(() -> {
                        lblTitle.setText("Player 1 with token 'Green'");
                        lblStatus.setText("Waiting for player 2 to join");
                    });

                    // Receive startup notification from the server
                    fromServer.readInt(); // Whatever read is ignored

                    // The other player has joined
                    Platform.runLater(() ->
                            lblStatus.setText("Player 2 has joined. I start first"));

                    // It is my turn
                    myTurn = true;
                }
                else if (player == PLAYER2) {
                    myToken = "RED";
                    otherToken = "GREEN";
                    Platform.runLater(() -> {
                        lblTitle.setText("Player 2 with token 'RED'");
                        lblStatus.setText("Waiting for player 1 to move");
                    });
                }

                // Continue to play
                while (continueToPlay) {
                    if (player == PLAYER1) {
                        waitForPlayerAction(); // Wait for player 1 to move
                        sendMove(); // Send the move to the server
                        receiveInfoFromServer(); // Receive info from the server
                    }
                    else if (player == PLAYER2) {
                        receiveInfoFromServer(); // Receive info from the server
                        waitForPlayerAction(); // Wait for player 2 to move
                        sendMove(); // Send player 2's move to the server
                    }
                }
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }).start();
    }

    /** Wait for the player to mark a cell */
    private void waitForPlayerAction() throws InterruptedException {
        while (waiting) {
            Thread.sleep(100);
        }

        waiting = true;
    }

    /** Send this player's move to the server */
    private void sendMove() throws IOException {
        toServer.writeInt(rowSelected); // Send the selected row
        toServer.writeInt(columnSelected); // Send the selected column
    }

    /** Receive info from the server */
    private void receiveInfoFromServer() throws IOException {
        // Receive game status
        int status = fromServer.readInt();

        if (status == PLAYER1_WON) {
            // Player 1 won, stop playing
            continueToPlay = false;
            if (myToken .equals( "GREEN")) {
                Platform.runLater(() -> lblStatus.setText("I won! (GREEN)"));
            }
            else if (myToken  .equals("RED")) {
                Platform.runLater(() ->
                        lblStatus.setText("Player 1 (GREEN) has won!"));
                receiveMove();
            }
        }
        else if (status == PLAYER2_WON) {
            // Player 2 won, stop playing
            continueToPlay = false;
            if (myToken .equals( 'O')) {
                Platform.runLater(() -> lblStatus.setText("I won! (RED)"));
            }
            else if (myToken .equals( "GREEN")) {
                Platform.runLater(() ->
                        lblStatus.setText("Player 2 (RED) has won!"));
                receiveMove();
            }
        }
        else if (status == DRAW) {
            // No winner, game is over
            continueToPlay = false;
            Platform.runLater(() ->
                    lblStatus.setText("Game is over, no winner!"));

            if (myToken .equals( "RED")) {
                receiveMove();
            }
        }
        else {
            receiveMove();
            Platform.runLater(() -> lblStatus.setText("My turn"));
            myTurn = true; // It is my turn
        }
    }

    private void receiveMove() throws IOException {
        // Get the other player's move
        int row = fromServer.readInt();
        int column = fromServer.readInt();
        Platform.runLater(() -> cell[row][column].setToken(otherToken));
    }

    // An inner class for a cell
    public class Cell extends Pane {
        // Indicate the row and column of this cell in the board
        private int row;
        private int column;

        // Token used for this cell
        private String token = " ";

        public Cell(int row, int column) {
            this.row = row;
            this.column = column;
            this.setPrefSize(2000, 2000); // What happens without this?
            setStyle("-fx-border-color: black"); // Set cell's border
            this.setOnMouseClicked(e -> handleMouseClick());
        }

        /**
         * Return token
         */
        public String getToken() {
            return token;
        }

        /**
         * Set a new token
         */
        public void setToken(String c) {
            token = c;
            repaint();
        }

        protected void repaint() {
            if (token== "GREEN") {
                Ellipse ellipse = new Ellipse(this.getWidth() / 2,
                        this.getHeight() / 2, this.getWidth() / 2 - 10,
                        this.getHeight() / 2 - 10);
                ellipse.centerXProperty().bind(
                        this.widthProperty().divide(2));
                ellipse.centerYProperty().bind(
                        this.heightProperty().divide(2));
                ellipse.radiusXProperty().bind(
                        this.widthProperty().divide(2).subtract(10));
                ellipse.radiusYProperty().bind(
                        this.heightProperty().divide(2).subtract(10));
                ellipse.setStroke(Color.GREEN);
                ellipse.setFill(Color.GREEN);

                // Add the lines to the pane
                //this.getChildren().addAll(line1, line2);
                getChildren().add(ellipse); // Add the ellipse to the pane
            } else if (token == "RED") {
                Ellipse ellipse = new Ellipse(this.getWidth() / 2,
                        this.getHeight() / 2, this.getWidth() / 2 - 10,
                        this.getHeight() / 2 - 10);
                ellipse.centerXProperty().bind(
                        this.widthProperty().divide(2));
                ellipse.centerYProperty().bind(
                        this.heightProperty().divide(2));
                ellipse.radiusXProperty().bind(
                        this.widthProperty().divide(2).subtract(10));
                ellipse.radiusYProperty().bind(
                        this.heightProperty().divide(2).subtract(10));
                ellipse.setStroke(Color.RED);
                ellipse.setFill(Color.RED);

                getChildren().add(ellipse); // Add the ellipse to the pane
            }
        }

        /* Handle a mouse click event */
        private void handleMouseClick() {
            // If cell is not occupied and the player has the turn
            if (token == " " && myTurn) {

                columnSelected = column;

                for (rowSelected = 5; rowSelected >= 0; rowSelected--) {
                    if (cell[rowSelected][columnSelected].token == " ") {
                        cell[rowSelected][columnSelected].setToken(myToken); // Set the player's token in the cell
                        break;
                    }
                }
                myTurn = false;
                lblStatus.setText("Waiting for the other player to move");
                waiting = false; // Just completed a successful move
            }
        }
    }

    /**
     * The main method is only needed for the IDE with limited
     * JavaFX support. Not needed for running from the command line.
     */
    public static void main(String[] args) {
        launch(args);
    }
}