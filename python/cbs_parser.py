import argparse
import glob
import os

from parser_builder import parse_file, cbs_file_parser


def main():
    if not os.path.isdir("out"):
        os.mkdir("out")

    parser = argparse.ArgumentParser(description="Parse .cbs files.")
    parser.add_argument(
        "-d",
        "--directory",
        help="Parse all .cbs files in the specified directory",
    )
    parser.add_argument(
        "-f",
        "--file",
        help="Parse the specified .cbs file",
    )
    parser.add_argument(
        "-j",
        "--json",
        help="Dump parsed data to JSON",
        action="store_true",
    )
    parser.add_argument(
        "-p",
        "--print",
        help="Print parsed data",
        action="store_true",
    )
    args = parser.parse_args()

    # If no arguments are provided, print help
    if not any(vars(args).values()):
        parser.print_help()
        return

    if args.directory and args.file:
        raise ValueError("Specify either -d/--directory or -f/--file, not both.")

    if args.directory:
        pattern = os.path.join(args.directory, "**/*.cbs")
        cbs_files = glob.glob(pattern, recursive=True)
        for path in cbs_files:
            print(path)
            parse_file(cbs_file_parser, path, dump_json=args.json, print_res=args.print)
    elif args.file:
        print(args.file)
        parse_file(
            cbs_file_parser, args.file, dump_json=args.json, print_res=args.print
        )
    else:
        raise ValueError("Specify either -d/--directory or -f/--file.")


if __name__ == "__main__":
    main()
