# JSFV
A simple collection of command line programs to read and write SFV files. With these files, you can create an SFV file for a collection of folders and files so that you always have a simple way to insure that your files have not been corrupted. You will also have a way to verify if files you have downloaded were not corrupted, provided that the source has provided an SFV file to check against.
# What is an SFV file?
An SFV file stands for Simple File Verification. It is a text file that contains a listing of file path names along with an 8-digit hexidecimal number or CRC code that represents that file. The file path name and CRC code are found on the same line and are usually separated by a single space or tab character, but can also be separated by any amount of whitespace. More info can be found on this [Wikipedia page](https://en.wikipedia.org/wiki/Simple_file_verification).
# How to use JSFV?
## JSFVWriter
JSFVWriter has the following command line arguments:
```
Usage: java JSFVWriter sfv -d:description dir | file [...]
        sfv  The name of the SFV file that is going to created.
             Files will be written to the SFV file relative to the SFV file's parent directory.
        dir  The directory whose files will be added to the SFV file.
             The directory will be traversed recursively.
        file The file to add to the SFV file.
```
Both of the `dir` and `file` arguments support the wildcard characters \* and ?. In this case, JSFVWriter will search for all matching files and directories that match the pattern.

All of the files and directories will be listed using a path that is relative to the SFV file being created. For instance, suppose the SFV file is being created at `a/b/file.sfv` and the directory `a/b/c` was being added to the file. The listing will be created as:
```
c/file1.txt
c/file2.txt
c/d/file3.txt
...
```

Multiple files and directories can be added to the SFV file by specifying each one as a separate command line argument.
## JSFVReader
JSFVReader has the following command line arguments:
```
Usage: java JSFVReader [-l] sfv [...]
        -l  Specify that the program should not follow symbolic links.
            If specified, then this is the first parameter.
        sfv An SFV file whose contents should be verified.
```
All relative file path names listed in the SFV file will be resolved against the SFV file’s parent directory. You can also verify multiple SFV files by specifying each one as a separate command line argument.
# Requirements
JSFV requires a Java Runtime Environment (JRE) of 1.7 or higher. In order to use the released Jar file, type the following into the console: `java -cp path/to/JSFV.jar` followed by either `JSFVReader` or `JSFVWriter` and then the appropriate arguments.
