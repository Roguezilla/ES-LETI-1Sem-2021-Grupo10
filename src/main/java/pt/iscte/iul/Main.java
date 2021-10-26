package pt.iscte.iul;

import javax.swing.*;
import java.io.File;

public class Main {

    /**
     * Main function to start the application.
     * Sees if the data file existe and if so reads the data.
     * Shows a pop-up window with a yes/no question.
     * If answer == yes loads the app with the saved data.
     * If answer == no proceed with the standard load.
     * If there is no data file also does the standard load.
     *
     * @param args nothing.
     * @author Rodrigo Guerreiro
     */
    public static void main(String[] args) {
        JFrame frame = new JFrame();
        String data = "data/user_data.txt";
        if (DataSaver.exists(new File(data))){
            String[] trello = DataReader.getUserTrelloData(data);
            String[] git = DataReader.getUserGitData(data);
            int opc = HomeUI.pop();
            if (opc ==JOptionPane.YES_OPTION){
                HomeUI.show_frame(frame);
                Action.do_action(frame, git, trello);
            }else if(opc == JOptionPane.NO_OPTION){
                new HomeUI();

            }

        }else
        new HomeUI();
    }
}
