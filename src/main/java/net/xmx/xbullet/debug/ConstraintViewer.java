package net.xmx.xbullet.debug;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

public class ConstraintViewer extends JFrame {

    private final JTextArea outputArea;

    public ConstraintViewer() {
        super("XBullet Constraint Viewer (Jolt-Independent)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        outputArea = new JTextArea("Drag 'constraints.bin' file here...");
        outputArea.setEditable(false);
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        outputArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(outputArea);
        add(scrollPane, BorderLayout.CENTER);

        setTransferHandler(new FileTransferHandler());
    }

    private void processFile(File file) {
        if (!file.getName().equals("constraints.bin")) {
            outputArea.setText("Error: Please drop a file named 'constraints.bin'.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Processing file: ").append(file.getAbsolutePath()).append("\n\n");

        try {
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            if (fileBytes.length == 0) {
                outputArea.setText(sb.toString() + "File is empty.");
                return;
            }

            FriendlyByteBuf fileBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(fileBytes));
            int constraintCount = 0;

            while (fileBuf.isReadable()) {
                if (fileBuf.readableBytes() < 16) { // Mindestens eine UUID
                    sb.append("...trailing bytes found, not enough for a full record.\n");
                    break;
                }
                constraintCount++;
                UUID constraintId = fileBuf.readUUID();

                if (fileBuf.readableBytes() < 4) { // Mindestens ein int für die Array-Länge
                    sb.append(String.format("--- Constraint #%d | ID: %s ---\n", constraintCount, constraintId));
                    sb.append("  ERROR: Incomplete data. Found ID but no data array follows.\n");
                    break;
                }
                byte[] constraintData = fileBuf.readByteArray(32767); // Lese bis zu 32k

                sb.append(String.format("--- Constraint #%d | ID: %s ---\n", constraintCount, constraintId));
                parseAndAppendConstraintDetails(sb, constraintData);
                sb.append("\n");
            }

            if (constraintCount == 0) {
                sb.append("No constraints found in file, but file is not empty. Possible corruption?");
            }

            outputArea.setText(sb.toString());

        } catch (Exception e) {
            outputArea.setText("An error occurred while reading the file:\n" + e.toString());
            e.printStackTrace();
        }
    }

    private void parseAndAppendConstraintDetails(StringBuilder sb, byte[] data) {
        FriendlyByteBuf dataBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));

        try {
            String typeId = dataBuf.readUtf();
            sb.append(String.format("  Type: %s\n", typeId));

            // Welt-Konstante lokal definieren
            UUID WORLD_BODY_ID = new UUID(0L, 0L);

            UUID body1Id = dataBuf.readUUID();
            UUID body2Id = dataBuf.readUUID();

            sb.append(String.format("  Body 1 UUID: %s\n", WORLD_BODY_ID.equals(body1Id) ? "WORLD" : body1Id.toString()));
            sb.append(String.format("  Body 2 UUID: %s\n", WORLD_BODY_ID.equals(body2Id) ? "WORLD" : body2Id.toString()));

            // Lese den Rest der Daten und zeige sie als Roh-Bytes an oder spezifisch
            if ("xbullet:hinge".equals(typeId)) {
                // EConstraintSpace Enum
                int enumOrdinal = dataBuf.readByte();
                sb.append(String.format("  Space Enum Ordinal: %d\n", enumOrdinal));

                // Point 1 (RVec3 -> 3 doubles)
                double p1x = dataBuf.readDouble();
                double p1y = dataBuf.readDouble();
                double p1z = dataBuf.readDouble();
                sb.append(String.format("  Point 1 (x, y, z): %.3f, %.3f, %.3f\n", p1x, p1y, p1z));

                // Point 2 (RVec3 -> 3 doubles)
                double p2x = dataBuf.readDouble();
                double p2y = dataBuf.readDouble();
                double p2z = dataBuf.readDouble();
                sb.append(String.format("  Point 2 (x, y, z): %.3f, %.3f, %.3f\n", p2x, p2y, p2z));

                // Hinge Axis 1 (Vec3 -> 3 floats)
                float ha1x = dataBuf.readFloat();
                float ha1y = dataBuf.readFloat();
                float ha1z = dataBuf.readFloat();
                sb.append(String.format("  Hinge Axis 1 (x, y, z): %.3f, %.3f, %.3f\n", ha1x, ha1y, ha1z));

                // ... füge hier nach Bedarf das Parsen weiterer Felder hinzu
            }

            int remainingBytes = dataBuf.readableBytes();
            if(remainingBytes > 0) {
                sb.append(String.format("  ... and %d more raw bytes of data.\n", remainingBytes));
            }

        } catch (Exception e) {
            sb.append(String.format("  ERROR parsing constraint data: %s\n", e.getMessage()));
        } finally {
            if (dataBuf.refCnt() > 0) {
                dataBuf.release();
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ConstraintViewer().setVisible(true));
    }

    private class FileTransferHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            Transferable transferable = support.getTransferable();
            try {
                List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                if (!files.isEmpty()) {
                    processFile(files.get(0));
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }
    }
}