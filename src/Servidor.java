import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Servidor {

    public static void main(String[] args) {
        final int PUERTO = 5000;
        byte[] buffer = new byte[1024];

        try (DatagramSocket socketUDP = new DatagramSocket(PUERTO)) {
            System.out.println("=========================================");
            System.out.println("Servidor UDP iniciado en puerto " + PUERTO);
            System.out.println("=========================================\n");

            while (true) {
                DatagramPacket peticion = new DatagramPacket(buffer, buffer.length);

                socketUDP.receive(peticion);

                DatagramPacket respuesta = new DatagramPacket(
                        peticion.getData(),
                        peticion.getLength(),
                        peticion.getAddress(),
                        peticion.getPort()
                );
                socketUDP.send(respuesta);

                String mensaje = new String(peticion.getData(), 0, peticion.getLength());
                System.out.println("Estímulo UDP recibido | Respuesta enviada: " + mensaje);
                System.out.println("-----------------------------------------");
            }

        } catch (SocketException ex) {
            Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}