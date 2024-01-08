const path = require('path');

module.exports = {
  target: 'node',
  mode: 'production',
  entry: path.resolve(__dirname, './src/index.ts'),
  devtool: 'inline-source-map',
  externals: ['aws-sdk'],
  module: {
    rules: [
      {
        test: /\.(ts)$/,
        use: 'ts-loader',
        exclude: /node_modules/,
      }
    ]
  },
  resolve: {
    extensions: ['.ts', '.js'],
    fallback: {},
  },
  output: {
    path: path.resolve(__dirname, './build'),
    filename: 'index.js',
    libraryTarget: 'commonjs'
  }
};