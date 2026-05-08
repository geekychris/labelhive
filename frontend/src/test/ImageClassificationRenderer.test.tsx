import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ImageClassificationRenderer } from '../sdk/renderers/ImageClassificationRenderer';

describe('ImageClassificationRenderer', () => {
  it('renders the image and the choice buttons', () => {
    const { container } = render(
      <ImageClassificationRenderer
        config={{ imageField: 'imageUrl', choices: ['cat', 'dog'] }}
        payload={{ imageUrl: 'https://example.test/x.jpg', caption: 'pet' }}
        value={undefined}
        onChange={() => {}}
      />,
    );
    const img = container.querySelector('img');
    expect(img).toHaveAttribute('src', 'https://example.test/x.jpg');
    expect(screen.getByText('pet')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /\bcat\b/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /\bdog\b/ })).toBeInTheDocument();
  });

  it('uses the configured imageField key', () => {
    const { container } = render(
      <ImageClassificationRenderer
        config={{ imageField: 'pic', choices: ['a'] }}
        payload={{ pic: 'https://example.test/y.jpg' }}
        value={undefined}
        onChange={() => {}}
      />,
    );
    expect(container.querySelector('img')).toHaveAttribute('src', 'https://example.test/y.jpg');
  });
});
