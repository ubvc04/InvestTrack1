import { TestBed } from '@angular/core/testing';

import { AiService } from './ai.service';
import { HttpClientTestingModule } from '@angular/common/http/testing';

describe('AiService', () => {
  let service: AiService;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(AiService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
