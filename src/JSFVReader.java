import static java.lang.System.err;
import static java.lang.System.out;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CheckedInputStream;
import java.util.zip.CRC32;

public class JSFVReader {
	public static void main(String args[]) {
		if (args.length == 0) {
			out.println("Usage: java JSFVReader [-l] sfv [...]");
			out.println("\t-l  Specify that the program should not follow symbolic links.");
			out.println("\t    If specified, then this is the first parameter.");
			out.println("\tsfv An SFV file whose contents should be verified.");
			return;
		}

		boolean nofollowlinks = args[0].equalsIgnoreCase("-l");
		LinkOption[] lnkOptions = (nofollowlinks ? new LinkOption[]{ LinkOption.NOFOLLOW_LINKS } : new LinkOption[0]);

		// Loop through all of the sfv files that have been specified
		// on the command line.
		// nofollowlinks determines where to start because that command
		// must be specified as the first command.
		for (int i = (nofollowlinks ? 1 : 0); i < args.length; i++) {
			Path sfv = Paths.get(args[i]);

			try {
				sfv = sfv.toRealPath(lnkOptions);
			} catch (IOException e) {
				err.println(e.getMessage());
				if (i != (args.length - 1)) out.println();
				continue;
			}

			if (Files.notExists(sfv, lnkOptions)) {
				out.printf("%s was not found.%n", sfv);
				continue;
			}

			if (Files.isDirectory(sfv, lnkOptions)) {
				out.printf("%s must be a file.%n", sfv);
				continue;
			}

			Path sfvParentDir = sfv.getParent();
			List<String> lines = null;
			int lineCount = 1;
			CRC32 crc = new CRC32();

			out.printf("Reading the contents of %s:%n%n", sfv);
			try {
				lines = Files.readAllLines(sfv, StandardCharsets.UTF_8);
			} catch (IOException e) {
				err.println(e.getMessage());
				if (i != (args.length - 1)) out.println();
				continue;
			}

			// Loop through the lines of the sfv file.
			// If a line starts with ';', then it is a comment and is ignored.
			for (String line : lines) {
				Pattern filePattern = Pattern.compile("^\\s*([^;#].+\\S)\\s+(0x)?([\\dA-Fa-f]{1,8})$");
				Matcher matcher = filePattern.matcher(line);
				// File line, group 1 is the file name, group 3 is the crc32
				if (matcher.matches()) {
					Path filePart = Paths.get(matcher.group(1));
					Path file = sfvParentDir.resolve(filePart);

					if (Files.exists(file, lnkOptions)) {
						crc.reset();

						try (CheckedInputStream cin = new CheckedInputStream(
						new BufferedInputStream(Files.newInputStream(file)), crc)) {
							long total = Files.size(file);
							long track = 0L;
							int read = -1;
							int lastPercent = 0;
							byte[] buffer = new byte[64];

							while ((read = cin.read(buffer)) != -1) {
								track += (long) read;
								int percent = (int) ((((double) track) / ((double) total)) * 100.0d);
								if (percent != lastPercent) {
									out.printf("\r%d%%", percent);
									lastPercent = percent;
								}
							}
						} catch (IOException e) {
							err.println(e.getMessage());
						}
					} else {
						out.printf("%s was not found.%n", file);
						continue;
					}

					out.printf("\r%s", filePart);
					try {
						long sum1 = Long.parseLong(matcher.group(3), 16);
						long sum2 = crc.getValue();

						if (sum1 == sum2) {
							out.println(" ... good.");
						} else {
							out.println(" ... bad.");
						}
					} catch (NumberFormatException nfe) {
						out.println(" ... error.");
					}
				} else {
					if (matcher.hitEnd() && (line.length() != 0)) {
						out.printf("Malformed SFV line (#%d): \"%s\"%n", lineCount, line);
					}
				}
				lineCount++;
			}
		}
	}
}
