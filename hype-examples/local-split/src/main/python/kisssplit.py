import random

random.seed(42)

def main(input_file, train_pct, eval_pct, cv_pct, train_file, eval_file, cv_file):
    assert abs(sum([train_pct, eval_pct, cv_pct]) - 1.0) < 0.000001
    with open(input_file, 'r') as f, open(train_file, 'w') as train_fd, open(eval_file,
                                                                             'w') as eval_fd, open(
        cv_file, 'w') as cv_fd:
        for line in f.readlines():
            rd = random.random()
            if (rd < train_pct):
                train_fd.write(line)
            elif (rd < train_pct + eval_pct):
                eval_fd.write(line)
            else:
                cv_fd.write(line)


if __name__ == '__main__':
    import argparse

    parser = argparse.ArgumentParser(
        description='Split file (line by line) into train, evaluation and cross validation data')
    parser.add_argument('input', type=str, help='input filename (local filesystem)')
    parser.add_argument('train_pct', type=float, help='train split: (0-1) percentage')
    parser.add_argument('eval_pct', type=float, help='evaluation split: (0-1) percentage')
    parser.add_argument('cv_pct', type=float, help='cross validation split: (0-1) percentage')
    parser.add_argument('train_file', type=str, help='output file for train split')
    parser.add_argument('eval_file', type=str, help='output file for evaluation split')
    parser.add_argument('cv_file', type=str, help='output file for cross validation split')
    args = parser.parse_args()
    main(args.input,
         args.train_pct,
         args.eval_pct,
         args.cv_pct,
         args.train_file,
         args.eval_file,
         args.cv_file)
