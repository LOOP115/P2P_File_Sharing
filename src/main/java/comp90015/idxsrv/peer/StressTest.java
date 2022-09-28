package comp90015.idxsrv.peer;

import java.util.ArrayList;

import comp90015.idxsrv.message.AuthenticateRequest;
import comp90015.idxsrv.server.ServerTextGUI;

public class StressTest {
    public static Integer numSuccess = 0;

    public static void main(String[] args) {

        AuthenticateRequest auth = new AuthenticateRequest("server123");
        String ipAddr = "172.26.130.95";
        int serverPort = 3200;
        ServerTextGUI stg = new ServerTextGUI();

        int numTest = 100;

        ArrayList<StressFunc> testList = new ArrayList<StressFunc>();

        // initialize threads
        for(int i=0; i<numTest; i++){
            StressFunc stressFunc = new StressFunc(auth, ipAddr, serverPort, stg);
            testList.add(stressFunc);
        }

        // Start all threads
        for(int i=0; i<numTest; i++){
            testList.get(i).start();
        }

        for(int i=0; i<numTest; i++){
            try {
                testList.get(i).join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        System.out.print(""+numSuccess);

    }

}


