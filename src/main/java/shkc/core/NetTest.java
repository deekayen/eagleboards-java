import java.net.DatagramSocket;

public class NetTest {
   public static void main(String[] var0) throws Exception {
      DatagramSocket var1 = new DatagramSocket();
      System.out.println("RBSize: " + var1.getReceiveBufferSize());
   }
}
