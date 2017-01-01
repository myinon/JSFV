import static java.lang.System.console;
import static java.lang.System.currentTimeMillis;
import static java.lang.System.err;
import static java.lang.System.exit;
import static java.lang.System.out;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.zip.CheckedInputStream;
import java.util.zip.CRC32;

public class JSFVWriter {
	private static CRC32 crc = new CRC32();

	private static int success = 0;
	private static int failed = 0;

	static void calculateCRC(Path file, Path sfvParent, List<String> lines) {
		if (Files.exists(file, NOFOLLOW_LINKS)) {
			StringBuilder builder = new StringBuilder();
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

				String value = Long.toHexString(crc.getValue()).toUpperCase();
				builder.append(sfvParent.relativize(file.toRealPath(NOFOLLOW_LINKS))).append(" ");

				for (int i = 8 - value.length(); i > 0; i--) {
					builder.append("0");
				}
				builder.append(value);
				out.printf("\r%s %s%n", file, value);
				lines.add(builder.toString());
				success++;
			} catch (IOException e) {
				err.printf("\r%s%n", e.getMessage());
				failed++;
			}
		} else {
			out.printf("%s was not found.%n", file);
			failed++;
		}
	}

	private static void usage() {
		out.println("Usage: java JSFVWriter sfv -d:description dir | file [...]");
		out.println("\tsfv  The name of the SFV file that is going to created.");
		out.println("\t     Files will be written to the SFV file relative to the SFV file's parent directory.");
		out.println("\tdir  The directory whose files will be added to the SFV file.");
		out.println("\t     The directory will be traversed recursively.");
		out.println("\tfile The file to add to the SFV file.");
	}

	public static void main(String args[]) {
		if (args.length == 0) {
			usage();
			return;
		}

		Path sfv = Paths.get(args[0]);

		try {
			sfv = sfv.toRealPath(NOFOLLOW_LINKS);
		} catch (IOException e) {
			if (Files.notExists(sfv, NOFOLLOW_LINKS)) {
				sfv = sfv.toAbsolutePath();
			} else {
				err.println(e.getMessage());
				exit(1);
			}
		}

		if (Files.isDirectory(sfv, NOFOLLOW_LINKS)) {
			out.printf("%s must be a file.%n", sfv);
			exit(2);
		}

		if (Files.exists(sfv, NOFOLLOW_LINKS)) {
			String answer = console().readLine("%s already exists. Overwrite the existing file (yes/no)? ", sfv);
			if (!(answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes"))) {
				return;
			}
		}

		String description = "";
		int listIdx = 1;

		if (args.length >= 2) {
			if (args[1].startsWith("-d:")) {
				description = args[1].substring(3);
				listIdx = 2;
			}
		}

		Path sfvParent = sfv.getParent();
		List<String> lines = new ArrayList<>();

		if (description.length() > 0) {
			lines.add("; SFV File for " + description);
		}
		lines.add("; Created on " + FileTime.fromMillis(currentTimeMillis()));
		lines.add("; File encoding is UTF-8");
		lines.add("");

		// remaining arguments are the source file(s) and/or directory(ies)
		int remaining = args.length - listIdx;
		if (remaining < 1)
			usage();
		List<Path> list = new ArrayList<>(remaining);
		while (remaining-- > 0) {
			Path p = Paths.get(args[listIdx++]);
			try {
				list.add(p.toRealPath(NOFOLLOW_LINKS));
			} catch (InvalidPathException e) {
				String input = e.getInput();
				int bslash = e.getIndex();
				if (bslash == -1 || (input.charAt(bslash) != '*' && input.charAt(bslash) != '?'))
					throw e;
				
				while (--bslash > 0 && input.charAt(bslash) != '/' && input.charAt(bslash) != '\\');
				
				// bslash == 0 means that no path separator
				//             was specified before the wildcard
				String parent = (bslash == 0) ? "./" : input.substring(0, bslash);
				// replaceAll is used to prevent Files.newDirectoryStream from using
				// parts of the file name as the glob pattern
				String pattern = input.substring((bslash == 0) ? bslash : bslash + 1).replaceAll("([^a-zA-Z0-9*?])", "\\\\$1");
				
				Path dir = Paths.get(parent);
				try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, pattern)) {
					for (Path pt : stream) {
						list.add(pt.toRealPath(NOFOLLOW_LINKS));
					}
				} catch (IOException x) {
					err.println(x.getMessage());
					exit(3);
				}
			} catch (IOException x) {
				if (Files.notExists(p, NOFOLLOW_LINKS)) {
					list.add(p.toAbsolutePath());
				} else {
					err.println(x.getMessage());
					exit(4);
				}
			}
		}
		Collections.sort(list);
		final Path[] source = list.toArray(new Path[0]);

		// Loop through the files and directories and add them to the file
		for (int i = 0; i < source.length; i++) {
			final Path src = source[i];
			boolean isDir = Files.isDirectory(src);

			if (isDir) {
				try {
					EnumSet<FileVisitOption> opts = EnumSet.noneOf(FileVisitOption.class);
					Files.walkFileTree(src, opts, Integer.MAX_VALUE, new FileVisitor<Path>() {
						@Override
						public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
							return CONTINUE;
						}

						@Override
						public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
							calculateCRC(file, sfvParent, lines);
							return CONTINUE;
						}

						@Override
						public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
							return CONTINUE;
						}

						@Override
						public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
							err.printf("Unable to visit path %s: %s%n", file, exc.getMessage());
							failed++;
							return CONTINUE;
						}
					});
				} catch (IOException e) {
					err.println(e.getMessage());
					failed++;
				}
			} else {
				calculateCRC(src, sfvParent, lines);
			}
		}

		out.println();
		out.printf("%d file(s) processed successfully%n%d file(s) failed to be processed%n", success, failed);

		try {
			Files.write(sfv, lines, StandardCharsets.UTF_8);
		} catch (IOException e) {
			err.println(e.getMessage());
		}
	}
}
