import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Cliente {

    public static void main(String[] args) {
        final int PUERTO_SERVIDOR = 5000;

        try (DatagramSocket socketUDP = new DatagramSocket()) {
            InetAddress direccionServidor = InetAddress.getByName("localhost");

            String mensaje = "prueba con mensaje largo";

            byte[] bufferEnvio = mensaje.getBytes();
            byte[] bufferRespuesta = new byte[1024];

            System.out.println("=========================================");
            System.out.println("     Iniciando Pruebas de Latencia UDP");
            System.out.println("=========================================\n");

            for (int i = 1; i <= 10; i++) {
                System.out.println("-----------------------------------------");
                System.out.println("Intento " + i + ": Enviando estímulo UDP: " + mensaje);

                DatagramPacket pregunta = new DatagramPacket(bufferEnvio, bufferEnvio.length, direccionServidor, PUERTO_SERVIDOR);
                DatagramPacket peticion = new DatagramPacket(bufferRespuesta, bufferRespuesta.length);

                long tiempoInicio = System.nanoTime();

                socketUDP.send(pregunta);
                socketUDP.receive(peticion);

                long tiempoFin = System.nanoTime();

                double latencia = (tiempoFin - tiempoInicio) / 1000000.0;

                System.out.println("Estímulo devuelto recibido.");
                System.out.printf("Latencia UDP: %.4f ms\n", latencia);
            }

            System.out.println("-----------------------------------------");
            System.out.println("Pruebas finalizadas. Desconectando...");

        } catch (SocketException ex) {
            Logger.getLogger(Cliente.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnknownHostException ex) {
            Logger.getLogger(Cliente.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Cliente.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}