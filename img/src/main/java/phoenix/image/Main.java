package phoenix.image;

import java.io.*;

public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args.length > 2) {
            showUsage();
            end();
            return;
        }

        File input = new File(args[0]);
        File output = args.length > 1 ? new File(args[1]) : new File("Reborn_" + input.getName());

        byte[] game = readGz(input);
        byte[] header = header(game);

        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        ba.write(header);
        ba.write(getSharedPatchData());
        ba.write(game);

        try (FileOutputStream out = new FileOutputStream(output)) {
            out.write(Deflate.gzip(ba.toByteArray()));
        }
        System.out.println(input.getName() + " transformed to " + output);
        end();
    }

    private static byte[] readGz(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            return Inflate.inflate(read(in, in.available()));
        }
    }

    private static void showUsage() {
        System.out.println("Usage: phoenix.exe path_to_input_map path_to_output_map");
    }

    private static void end() throws IOException, InterruptedException {
        while (true) {
            Thread.sleep(1000000);
        }
    }

    private static byte[] header(byte[] game) throws IOException {
        HMap.HeroStream stream = new HMap.HeroStream(game);
        HMap.MapHeader header = HMap.MapHeader.read(stream);

        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        ba.write(stream.in, 0, stream.p);
        ba.write(new byte[18]); // artefacts
        ba.write(new byte[9]); // spells
        ba.write(new byte[4]); // skills
        ba.write(new byte[4]);  // rumors (count = 0)
        ba.write(new byte[156]);  // hero options (enabled = 0)
        ba.write(new byte[7 * header.size * header.size * (header.twoLevel + 1)]); // cells
        ba.write(new byte[]{1,0,0,0});   // templates_count = 1
        ba.write(new byte[]{(byte) "AVXPrsn0.def\0".length(),0,0,0});
        ba.write("AVXPrsn0.def\0".getBytes());
        ba.write(new byte[6]);  // passability
        ba.write(new byte[6]);  // actions
        ba.write(new byte[2]);  // landscape
        ba.write(new byte[2]);  // land_edit_groups
        ba.write(new byte[] { 0x3E, 0, 0, 0 }); // prison
        ba.write(new byte[4]);  // object number
        ba.write(new byte[1]);  // object group
        ba.write(new byte[1]);  // overlay
        ba.write(new byte[16]);  // junk

        ba.write(new byte[]{1,0,0,0});  // objects_count = 1
        ba.write(new byte[12]); // coords, template, junk
        ba.write(new byte[4]);  // hero id
        ba.write(new byte[1]);  // color
        ba.write(new byte[1]);  // hero
        ba.write(new byte[]{1});  // has name

        return ba.toByteArray();
    }

    private static byte[] getSharedPatchData() throws IOException {
        InputStream s = Main.class.getResourceAsStream("/patch.dat");
        return read(s, s.available());
    }

    private static byte[] read(InputStream s, int size) throws IOException {
        byte[] buf = new byte[size];
        int pos = 0;
        while (pos < buf.length) {
            int read = s.read(buf, pos, buf.length - pos);
            if (read == -1) {
                break;
            }
            if (read == 0) {
                throw new IllegalStateException();
            }
            pos += read;
        }
        return buf;
    }
}
