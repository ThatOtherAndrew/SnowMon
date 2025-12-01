# Refactored with assistance from Claude Code

import argparse
import re
from xml.etree import ElementTree
from pathlib import Path


parser = argparse.ArgumentParser()
parser.add_argument('input_path', type=Path)
parser.add_argument('output_path', type=Path)
parser.add_argument('--inject', action='store_true')
parser.add_argument('--delete-source', action='store_true')

def parse_junit_xml(xml_tree: ElementTree) -> dict[str, dict[str, str]]:
    parsed_data = {}

    for suite in xml_tree.findall('suite'):
        suite_name = suite.get('name')
        if suite_name[-4:].lower() == 'test':
            suite_name = suite_name[:-4]
        parsed_data[suite_name] = {}
        for test in suite.findall('test'):
            parsed_data[suite_name][test.get('name')] = test.get('status')
    else:
        root_suite = {}
        for test in xml_tree.findall('test'):
            root_suite[test.get('name')] = test.get('status')
        # Only add root suite if it has tests
        if root_suite:
            parsed_data[xml_tree.find('root').get('name')] = root_suite

    return parsed_data


def make_markdown_table(data: dict[str, dict[str, str]]) -> str:
    header = 'Test suite', 'Test name', 'Status'
    results = {
        'passed': 'Pass',
        'failed': '**Fail**',
    }

    # Calculate widths, handling empty suites
    suite_name_width = 4 + max(map(len, data.keys())) if data else len(header[0])

    # Get max test name length from non-empty suites
    test_names = [test for suite in data.values() for test in suite.keys()]
    test_name_width = max(map(len, test_names)) if test_names else len(header[1])

    # Get max status length from non-empty suites
    statuses = [results[status] for suite in data.values() for status in suite.values()]
    status_width = max(map(len, statuses)) if statuses else len(header[2])

    widths = (
        max(len(header[0]), suite_name_width),
        max(len(header[1]), test_name_width),
        max(len(header[2]), status_width),
    )

    table = '| ' + ' | '.join(header[i].ljust(widths[i]) for i in range(3)) + ' |\n'
    table += '|' + '|'.join('-' * (widths[i] + 2) for i in range(3)) + '|\n'
    for suite_name, suite in sorted(data.items()):
        suite_name = f'**{suite_name}**'
        suite = {test: results[status] for test, status in suite.items()}
        first_row = True
        for test, status in sorted(suite.items()):
            table += f'| {suite_name.ljust(widths[0])} | {test.ljust(widths[1])} | {status.ljust(widths[2])} |\n'
            if first_row:
                suite_name = ' ' * widths[0]
                first_row = False

    return table


def main(args: argparse.Namespace) -> None:
    if not args.input_path.is_file():
        raise ValueError('provided path is not a valid file')
    tree = ElementTree.parse(args.input_path)
    if tree.getroot().tag != 'testrun':
        raise ValueError('provided file is not a valid JUnit XML file')

    results = parse_junit_xml(tree)
    table = make_markdown_table(results)

    if args.inject:
        pattern = re.compile(r'<!--\s*BEGIN\s+tests\s*-->.*?<!--\s*END\s+tests\s*-->', flags=re.DOTALL)
        with args.output_path.open('r+') as output_file:
            content = output_file.read()
            if not pattern.search(content):
                raise ValueError('output file has no boundary tags to inject tests table between')
            output_file.seek(0)
            output_file.write(pattern.sub(fr'<!-- BEGIN tests -->\n{table}<!-- END tests -->', content))
            output_file.truncate()
    else:
        with args.output_path.open('w') as output_file:
            output_file.write(table)

    if args.delete_source:
        args.input_path.unlink()


if __name__ == '__main__':
    main(parser.parse_args())
